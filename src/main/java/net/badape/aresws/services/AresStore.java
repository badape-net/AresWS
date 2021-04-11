package net.badape.aresws.services;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.*;
import lombok.extern.slf4j.Slf4j;
import net.badape.aresws.EventTopic;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class AresStore extends AbstractVerticle {

    private Pool pool;

    public void start() {
        pool = Pool.pool(vertx, getPgConnectOptions(), getPoolOptions());
        EventBus eb = vertx.eventBus();
        eb.<JsonObject>consumer(EventTopic.NEW_ACCOUNT, this::newAccount);
        eb.<JsonObject>consumer(EventTopic.BUY_HERO, this::buyHero);
        eb.<JsonObject>consumer(EventTopic.GET_ROSTER, this::getRoster);
        eb.<JsonObject>consumer(EventTopic.GET_TEAM, this::getTeam);
        eb.<JsonArray>consumer(EventTopic.HERO_REFRESH, this::heroRefresh);

        vertx.setPeriodic(config().getLong("heroRefresh", 60000L), id -> {
            requestHeroConfig();
        });
        requestHeroConfig();
    }

    private void requestHeroConfig() {
        JsonObject message = new JsonObject();
        EventBus eb = vertx.eventBus();
        eb.<JsonArray>request(EventTopic.GET_CHARACTERS_CONFIG, message, reply -> {
            if (reply.succeeded()) {
                heroRefresh(reply.result());
            } else {
                log.error(reply.cause().getLocalizedMessage());
            }
        });
    }

    private static final String SELECT_TEAM =
            "SELECT title as name, experience, kills, deaths FROM public.roster_view WHERE account_fk IS NOT NULL AND account_pk = $1";

    private static final String SELECT_BALANCE = "SELECT balance FROM public.account a2 WHERE account_pk = $1";

    private void getTeam(Message<JsonObject> message) {
        final Long accountId = message.body().getLong("accountId");
        final JsonArray sqlParams = new JsonArray().add(accountId);

        pool.preparedQuery(SELECT_BALANCE).execute(Tuple.of(accountId), balanceResult -> {
            if (balanceResult.succeeded()) {
                pool.preparedQuery(SELECT_TEAM).execute(Tuple.of(accountId), teamResult -> {
                    if (teamResult.succeeded()) {
                        JsonObject reply = new JsonObject();
                        JsonArray roster = new JsonArray();

                        balanceResult.result().forEach(row -> {
                            reply.put("credits", row.toJson().getLong("balance", 0L));
                        });

                        teamResult.result().forEach(row -> {
                            roster.add(row.toJson());
                        });

                        reply.put("characters", roster);

                        message.reply(reply);

                    } else {
                        log.error("Failure: " + teamResult.cause().getMessage());
                        message.fail(500, teamResult.cause().getMessage());
                    }
                });
            } else {
                log.error("Failure: " + balanceResult.cause().getMessage());
                message.fail(500, balanceResult.cause().getMessage());
            }
        });
    }

    private static final String SELECT_ROSTER =
            "SELECT title, hero_pk, game_idx, balance, level, experience, kills, deaths, credits, health, mana, stamina, spawn_cost FROM public.roster_view WHERE roster_view.account_pk = $1";

    private void getRoster(Message<JsonObject> message) {
        final Long accountId = message.body().getLong("accountId");

        pool.preparedQuery(SELECT_ROSTER).execute(Tuple.of(accountId), ar -> {
            if (ar.succeeded()) {

                JsonArray roster = new JsonArray();
                ar.result().forEach(row -> {
                    roster.add(row.toJson());
                });

//                JsonObject response = new JsonObject().put("data", roster);
                message.reply(roster);

            } else {
                log.error("Failure: " + ar.cause().getMessage());
                message.fail(500, ar.cause().getMessage());
            }
        });
    }

    private static final String INSERT_NEW_ACCOUNT = "INSERT INTO public.account(account_pk, balance) VALUES ($1, $2)";

    private void newAccount(Message<JsonObject> message) {
        final Long accountId = message.body().getLong("accountId");
        Integer credits = config().getInteger("defaultCredits", 100000);

        pool.preparedQuery(INSERT_NEW_ACCOUNT).execute(Tuple.of(accountId, credits), ar -> {
            if (ar.succeeded()) {
                RowSet<Row> rows = ar.result();
                log.info("Got " + rows.size() + " rows ");
            } else {
                log.error("Failure: " + ar.cause().getMessage());
            }
        });
    }

    private final static String INSERT_ROSTER_VIEW =
            "INSERT INTO public.roster_view(account_pk, title) VALUES ($1,$2)";

    private void buyHero(Message<JsonObject> message) {

        final Long accountId = message.body().getLong("accountId", null);
        final String title = message.body().getString("title", null);

        pool.preparedQuery(INSERT_ROSTER_VIEW).execute(Tuple.of(accountId, title), ar -> {
            if (ar.failed()) {
                log.error(ar.cause().getMessage());
                message.fail(500, ar.cause().getMessage());
            } else {
                getTeam(message);
            }
        });
    }

    private static final String UPSERT_HEROES_CONFIG =
            "INSERT INTO public.hero(title, credits) VALUES ($1, $2) " +
                    "ON CONFLICT ON CONSTRAINT pk_hero DO " +
                    "UPDATE SET title=$3, credits=$4 " +
                    "WHERE hero.title=$5";

    private void heroRefresh(Message<JsonArray> message) {


        List<Tuple> batch = new ArrayList<>();
        message.body().forEach(heroObject -> {
            JsonObject hero = (JsonObject) heroObject;
            String title = hero.getString("Name");
            Long credits = hero.getLong("Credits");
            batch.add(Tuple.of(title, credits, title, credits, title));
        });

        pool.preparedQuery(UPSERT_HEROES_CONFIG).executeBatch(batch, res -> {
            if (res.succeeded()) {
                message.reply(new JsonObject().put("updates", res.result().rowCount()));
            } else {
                message.fail(500, res.cause().getMessage());
            }
        });
    }

    private PgConnectOptions getPgConnectOptions() {

        return new PgConnectOptions()
                .setPort(config().getInteger("port", 5432))
                .setHost(config().getString("host", "localhost"))
                .setDatabase(config().getString("databaseName", "aresstore"))
                .setUser(config().getString("username", "postgres"))
                .setPassword(config().getString("password", "changeme"));
    }

    private PoolOptions getPoolOptions() {
        return new PoolOptions().setMaxSize(5);
    }
}
