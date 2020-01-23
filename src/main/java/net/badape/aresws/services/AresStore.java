package net.badape.aresws.services;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import net.badape.aresws.EventTopic;
import net.badape.aresws.db.AbstractDataVerticle;

import java.util.ArrayList;
import java.util.List;

public class AresStore extends AbstractDataVerticle {

    private final Logger log = LoggerFactory.getLogger( AresStore.class );
    private EventBus eb;

    @Override
    public void start(Future<Void> startFuture) {
        getConnection("aresstore", result -> {
            if (result.succeeded()) {
                eb = vertx.eventBus();
                eb.<JsonObject>consumer(EventTopic.NEW_ACCOUNT, this::newAccount);
                eb.<JsonObject>consumer(EventTopic.BUY_HERO, this::buyHero);
                eb.<JsonObject>consumer(EventTopic.GET_ROSTER, this::getRoster);
                eb.<JsonObject>consumer(EventTopic.GET_TEAM, this::getTeam);
                eb.<JsonObject>consumer(EventTopic.STORE_REFRESH, this::refreshData);

            } else {
                startFuture.fail(result.cause());
            }
        });
    }

    private static final String SELECT_TEAM =
            "SELECT title, level, experience FROM public.roster_view WHERE account_fk IS NOT NULL AND account_pk = ?";


    private void getTeam(Message<JsonObject> message) {

        final Long accountId = message.body().getLong("accountId");
        final JsonArray sqlParams = new JsonArray().add(accountId);

        conn.queryWithParams(SELECT_TEAM, sqlParams, result -> {
            if (result.failed()) {
                log.error(result.cause().getMessage());
                message.fail(500, result.cause().getMessage());
                return;
            }
            JsonObject response = new JsonObject().put("data", new JsonArray(result.result().getRows()));
            message.reply(response);
        });

    }

    private static final String SELECT_ROSTER =
            "SELECT title, hero_pk, game_idx, balance, level, experience, kills, deaths, credits, health, mana, stamina, spawn_cost FROM public.roster_view WHERE roster_view.account_pk = ?";

    private void getRoster(Message<JsonObject> message) {
        final Long accountId = message.body().getLong("accountId");
        final JsonArray sqlParams = new JsonArray().add(accountId);

        conn.queryWithParams(SELECT_ROSTER, sqlParams, result -> {
            if (result.failed()) {
                log.error(result.cause().getMessage());
                message.fail(500, result.cause().getMessage());
                return;
            }

            JsonObject response = new JsonObject().put("data", new JsonArray(result.result().getRows()));

            message.reply(response);
        });
    }

    private static final String INSERT_NEW_ACCOUNT = "INSERT INTO public.account(account_pk, balance) VALUES (?, ?)";

    private void newAccount(Message<JsonObject> message) {
        final Long accountId = message.body().getLong("accountId");
        final JsonArray sqlParams = new JsonArray().add(accountId).add(1000000);

        conn.setAutoCommit(false, rTx -> {
            if (rTx.failed()) {
                log.error(rTx.cause().getMessage());
                message.fail(500, rTx.cause().getMessage());
                return;
            }

            conn.updateWithParams(INSERT_NEW_ACCOUNT, sqlParams, cDevPlayer -> {
                if (cDevPlayer.failed()) {
                    log.error(cDevPlayer.cause().getMessage());
                    message.fail(500, cDevPlayer.cause().getMessage());
                    return;
                }

                conn.commit(cRes -> {
                    if (cRes.failed()) {
                        log.error(cRes.cause().getMessage());
                        message.fail(500, cRes.cause().getMessage());
                        return;
                    }

                    JsonObject reply = new JsonObject()
                            .put("accountId", accountId);

                    message.reply(reply);
                });
            });
        });

    }

    private final static String INSERT_ROSTER_VIEW =
            "INSERT INTO public.roster_view(account_pk, hero_fk) VALUES (?,?)";

    private void buyHero(Message<JsonObject> message) {

        final Long accountId = message.body().getLong("accountId", null);
        final Long heroId = message.body().getLong("heroId", -1L);

        JsonArray sqlParams = new JsonArray().add(accountId).add(heroId);

        log.info("INSERT_ROSTER_VIEW "+ sqlParams.encode());

        Future<UpdateResult> future = sqlTransaction(INSERT_ROSTER_VIEW, sqlParams);
        future.setHandler(result -> {
            if (result.failed()) {
                message.fail(500, result.cause().getMessage());
            }
            getRoster(message);

        });
    }

    private void refreshData(Message<JsonObject> message) {

        WebClient client = WebClient.create(vertx);

        String space = config().getString("CONTENTFUL_SPACE_ID");
        String token = config().getString("CONTENTFUL_ACCESS_TOKEN");

        HttpRequest<Buffer> request = client.get(443, "cdn.contentful.com", "/spaces/" + space + "/entries")
                .ssl(true);
        request.addQueryParam("content_type", "hero");
        request.putHeader("Authorization", "Bearer " + token);
        request.send(result -> {
            if (result.succeeded()) {
                final List<Future> lFutures = new ArrayList<>();
                conn.setAutoCommit(false, rTx -> {
                    if (rTx.failed()) {/**/
                        message.fail(500, rTx.cause().getMessage());
                        return;
                    }

                    JsonArray heroConfig = result.result().bodyAsJsonObject().getJsonArray("items");

                    heroConfig.forEach(object -> {
                        if (object instanceof JsonObject) {
                            JsonObject hero = (JsonObject) object;
                            lFutures.add(writeHero(hero));
                        }
                    });

                    CompositeFuture.all(lFutures).setHandler(lRes -> {
                        if (lRes.failed()) {
                            conn.rollback(rbRes -> {
                                message.fail(500, lRes.cause().getMessage());
                            });

                            return;
                        }

                        conn.commit(cRes -> {
                            if (cRes.failed()) {
                                message.fail(500, cRes.cause().getMessage());
                            }
                            message.reply(new JsonObject());

                        });

                    });
                });
            } else {
                log.error(result.cause().getMessage());
                message.fail(500,result.cause().getMessage());
            }
        });
    }

    private static final String UPSERT_HEROES_CONFIG =
            "INSERT INTO public.hero(hero_pk, game_idx, title, credits, description, health, mana, stamina, spawn_cost) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT ON CONSTRAINT pk_hero DO " +
                    "UPDATE SET game_idx=?, title=?, credits=?, description=?, health=?, mana=?, stamina=?, spawn_cost=? " +
                    "WHERE hero.hero_pk=?";

    // [name, description, faction, slug, heroImage, credits, health, mana, stamina, limit, gameid, heroid, publishDate]
    private Future writeHero(JsonObject hero) {
        Future<Void> future = Future.future();

        Long heroId = hero.getJsonObject("fields").getLong("heroid");
        Long gameIdx = hero.getJsonObject("fields").getLong("gameid");
        String title = hero.getJsonObject("fields").getString("name");
        Long credits = hero.getJsonObject("fields").getLong("credits");
        String description = hero.getJsonObject("fields").getString("description");
        Long health = hero.getJsonObject("fields").getLong("health");
        Long mana = hero.getJsonObject("fields").getLong("mana");
        Long stamina = hero.getJsonObject("fields").getLong("stamina");
        Long spawnCost = hero.getJsonObject("fields").getLong("spawn_cost", 0L);

        JsonArray sqlParams = new JsonArray()
                .add(heroId).add(gameIdx).add(title).add(credits).add(description).add(health).add(mana).add(stamina).add(spawnCost)
                .add(gameIdx).add(title).add(credits).add(description).add(health).add(mana).add(stamina).add(spawnCost)
                .add(heroId);

        conn.updateWithParams(UPSERT_HEROES_CONFIG, sqlParams, cPlayer -> {
            if (cPlayer.failed()) {
                log.error("writeHero "+ cPlayer.cause().getMessage());
                future.fail(cPlayer.cause());
                return;
            }
            future.complete();
        });

        return future;
    }


}
