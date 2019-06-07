package net.badape.aresws.actors;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import net.badape.aresws.EventTopic;
import net.badape.aresws.db.AbstractDataVerticle;
import net.badape.aresws.db.LiquibaseVerticle;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class HeroStoreActor extends AbstractDataVerticle {

    private EventBus eb;

    @Override
    public void start(Future<Void> startFuture) {
        schema = "store";

        DeploymentOptions liquiOpts = new DeploymentOptions()
                .setConfig(getDBConfig("store", "classpath:db/store.changelog.xml"))
                .setWorker(true);

        final List<Future> lFutures = new ArrayList<>();
        lFutures.add(liquibaseCycle(LiquibaseVerticle.class.getName(), liquiOpts));
        CompositeFuture.all(lFutures).setHandler(lRes -> {
            if (lRes.failed()) {
                startFuture.fail(lRes.cause());
            } else {
                getConnection(result -> {
                    if (result.succeeded()) {
                        loadData(startFuture);
                        eb = vertx.eventBus();
                        eb.<JsonObject>consumer(EventTopic.NEW_ACCOUNT, this::newAccount);
                        eb.<JsonObject>consumer(EventTopic.BUY_HERO, this::buyHero);
                        eb.<JsonObject>consumer(EventTopic.GET_ROSTER, this::getRoster);
                        eb.<JsonObject>consumer(EventTopic.GET_TEAM, this::getTeam);

                    } else {
                        startFuture.fail(result.cause());
                    }
                });
            }
        });
    }

    private static final String SELECT_TEAM =
            "SELECT title, level, experience FROM store.roster_view WHERE account_fk IS NOT NULL AND account_pk = ?";


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
            "SELECT title, hero_pk, game_idx, balance, level, experience, kills, deaths, credits, health, mana, stamina, spawn_cost FROM store.roster_view WHERE roster_view.account_pk = ?";

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

    private static final String INSERT_NEW_ACCOUNT = "INSERT INTO store.account(account_pk, balance) VALUES (?, ?)";

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
            "INSERT INTO store.roster_view(account_pk, hero_fk) VALUES (?,?)";

    private void buyHero(Message<JsonObject> message) {

        final Long accountId = message.body().getLong("accountId", null);
        final Long heroId = message.body().getLong("heroId", -1L);

        JsonArray sqlParams = new JsonArray().add(accountId).add(heroId);

        Future<UpdateResult> future = sqlTransaction(INSERT_ROSTER_VIEW, sqlParams);
        future.setHandler(result -> {
            if (result.failed()) {
                message.fail(500, result.cause().getMessage());
            }
            getRoster(message);

        });
    }

    private void loadData(Handler<AsyncResult<Void>> hndlr) {
        WebClient client = WebClient.create(vertx);

        client.get(443, "badape.online", "/hero/index.json").ssl(true).send(ar -> {
            if (ar.succeeded()) {
                // Obtain response
                HttpResponse<Buffer> response = ar.result();

                JsonArray heroConfig = response.bodyAsJsonObject().getJsonObject("data").getJsonArray("items");
                final List<Future> lFutures = new ArrayList<>();

                conn.setAutoCommit(false, rTx -> {
                    if (rTx.failed()) {
                        hndlr.handle(Future.failedFuture(rTx.cause()));
                        return;
                    }

                    heroConfig.forEach(object -> {
                        if (object instanceof JsonObject) {
                            JsonObject hero = (JsonObject) object;
                            lFutures.add(writeHero(hero));
                        }
                    });

                    CompositeFuture.all(lFutures).setHandler(lRes -> {
                        if (lRes.failed()) {
                            conn.rollback(rbRes -> {
                                hndlr.handle(Future.failedFuture(lRes.cause()));
                            });

                            return;
                        }

                        conn.commit(cRes -> {
                            if (cRes.failed()) {
                                hndlr.handle(Future.failedFuture(cRes.cause()));
                                return;
                            }
                            hndlr.handle(Future.succeededFuture());
                        });

                    });
                });

            } else {
                log.error("Something went wrong " + ar.cause().getMessage());
            }
        });
    }

    private static final String UPSERT_HEROES_CONFIG =
            "INSERT INTO store.hero(hero_pk, game_idx, title, credits, description, health, mana, stamina, spawn_cost) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT ON CONSTRAINT hero_pkey DO " +
                    "UPDATE SET game_idx=?, title=?, credits=?, description=?, health=?, mana=?, stamina=?, spawn_cost=? " +
                    "WHERE hero.hero_pk=?";

    private Future writeHero(JsonObject hero) {
        Future<Void> future = Future.future();

        Long heroId = hero.getLong("hero_id");
        Long gameIdx = hero.getLong("game_id");
        String title = hero.getString("title");
        Long credits = hero.getLong("credits");
        String description = hero.getString("content");
        Long health = hero.getLong("health");
        Long mana = hero.getLong("mana");
        Long stamina = hero.getLong("stamina");
        Long spawnCost = hero.getLong("spawn_cost");


        JsonArray sqlParams = new JsonArray()
                .add(heroId).add(gameIdx).add(title).add(credits).add(description).add(health).add(mana).add(stamina).add(spawnCost)
                .add(gameIdx).add(title).add(credits).add(description).add(health).add(mana).add(stamina).add(spawnCost)
                .add(heroId);

        conn.updateWithParams(UPSERT_HEROES_CONFIG, sqlParams, cPlayer -> {
            if (cPlayer.failed()) {
                future.fail(cPlayer.cause());
                return;
            }
            future.complete();
        });

        return future;
    }


}
