package net.badape.aresws.actors;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.badape.aresws.EventTopic;
import net.badape.aresws.db.AbstractDataVerticle;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class StatsActor extends AbstractDataVerticle {

    @Override
    public void start(Future<Void> startFuture) {
        schema = "stats";
        EventBus eb = vertx.eventBus();

        eb.<JsonObject>consumer(EventTopic.NEW_ROSTER_ACCOUNT, this::newRosterAccount);
        eb.<JsonObject>consumer(EventTopic.GET_PLAYER_STATS, this::getPlayerStats);
        eb.<JsonObject>consumer(EventTopic.GET_PLAYER_ROSTER, this::getPlayerRoster);
        eb.<JsonObject>consumer(EventTopic.NEW_ROSTER_HERO, this::newRosterHero);

        getConnection(result -> {
            if (result.succeeded()) {
                loadData(startFuture.completer());
            } else {
                startFuture.fail(result.cause());
            }
        });
    }

    private void newRosterHero(Message<JsonObject> message) {
        final Long playerId = message.body().getLong("playerId", null);
        final Long heroId = message.body().getLong("heroId", -1L);

        newRosterHeroQuery(playerId, heroId, result -> {
            if (result.failed()) {
                message.fail(500, result.cause().getMessage());
                return;
            }

            getStatsAccountQuery(playerId, stats -> {
                JsonObject reply = result.result();
                reply.put("stats", stats);
                log.info(reply.encode());
                message.reply(reply);
            });


        });
    }

    private final static String SQL_ADD_ROSTER =
            "INSERT INTO stats.roster (player_id, hero_id) VALUES (?, ?)";

    private void newRosterHeroQuery(final Long playerId, final Long heroId, Handler<AsyncResult<JsonObject>> hndlr) {

        log.info("playerid: " + playerId);

        JsonArray rosterParams = new JsonArray().add(playerId).add(heroId);
        conn.setAutoCommit(false, rTx -> {
            if (rTx.failed()) {
                hndlr.handle(Future.failedFuture(rTx.cause().getMessage()));
                return;
            }

            conn.updateWithParams(SQL_ADD_ROSTER, rosterParams, rosterResult -> {
                if (rosterResult.failed()) {
                    String errMsg = rosterResult.cause().getMessage();
                    log.error(errMsg);
                    hndlr.handle(Future.failedFuture(rosterResult.cause()));
                    return;
                }

                conn.commit(rCm -> {
                    if (rCm.failed()) {
                        hndlr.handle(Future.failedFuture(rCm.cause().getMessage()));
                        return;
                    }

                    rosterParams.clear();
                    rosterParams.add(playerId);
                    conn.queryWithParams(SELECT_STATS_ROSTER, rosterParams, qRes -> {
                        if (qRes.failed()) {
                            hndlr.handle(Future.failedFuture(qRes.cause().getMessage()));
                            return;
                        }

                        JsonArray roster = new JsonArray(qRes.result().getRows());
                        JsonObject reply = new JsonObject().put("roster", roster);
                        hndlr.handle(Future.succeededFuture(reply));
                    });
                });
            });
        });
    }

    private void getPlayerStats(Message<JsonObject> message) {
        getStatsAccountQuery(message.body().getLong("playerId"), message::reply);
    }

    private void getPlayerRoster(Message<JsonObject> message) {
        getPlayerRosterQuery(message.body().getLong("playerId"), message::reply);
    }


    private static final String SELECT_STATS_ACCOUNT = "SELECT * FROM stats.account WHERE player_id = ?";

    private void getStatsAccountQuery(Long playerId, Handler<JsonObject> hndlr) {

        log.info("getStatsAccountQuery playerId: " + playerId);

        final JsonArray sqlParams = new JsonArray().add(playerId);
        conn.queryWithParams(SELECT_STATS_ACCOUNT, sqlParams, qRes -> {
            if (qRes.failed()) {
                hndlr.handle(new JsonObject());
                return;
            }

            JsonObject reply = qRes.result().getRows().get(0);
            reply.remove("player_id");

            hndlr.handle(reply);
        });
    }

    private final static String SELECT_STATS_ROSTER =
            "SELECT * FROM stats.roster JOIN stats.account ON roster.player_id = account.player_id " +
                    "JOIN stats.hero_base ON roster.hero_id = hero_base.hero_id " +
                    "WHERE account.player_id = ?";

    private void getPlayerRosterQuery(Long playerId, Handler<JsonArray> hndlr) {

        log.info("getPlayerRosterQuery playerId: " + playerId);

        final JsonArray sqlParams = new JsonArray().add(playerId);
        log.info("getPlayerRosterQuery: " + sqlParams.encode());
        conn.queryWithParams(SELECT_STATS_ROSTER, sqlParams, rRes -> {
            if (rRes.failed()) {
                hndlr.handle(new JsonArray());
                return;
            }

            JsonArray roster = new JsonArray(rRes.result().getRows());
            hndlr.handle(roster);
        });
    }

    private void loadData(Handler<AsyncResult<Void>> hndlr) {

        vertx.fileSystem().readFile("config/heroes.json", result -> {
            if (result.failed()) {
                log.error(result.cause().getMessage());
                hndlr.handle(Future.failedFuture(result.cause()));
                return;
            }

            JsonArray heroConfig = result.result().toJsonArray();

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
                    } else {
                        conn.commit(cRes -> {
                            hndlr.handle(Future.succeededFuture());
                        });
                    }
                });
            });
        });
    }

    public static final String UPSERT_HERO_CONFIG =
            "INSERT INTO stats.hero_base(hero_id, game_id, health, mana, stamina, spawn_cost) VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT ON CONSTRAINT hero_base_pkey DO " +
                    "UPDATE SET game_id=?, health=?, mana=?, stamina=?, spawn_cost=? " +
                    "WHERE hero_base.hero_id=?";

    private Future writeHero(JsonObject hero) {

        Future<Void> future = Future.future();

        Long heroId = hero.getLong("hero_id");
        Long gameId = hero.getLong("game_id");
        Long health = hero.getLong("health");
        Long mana = hero.getLong("mana");
        Long stamina = hero.getLong("stamina");
        Long spawnCost = hero.getLong("spawn_cost");


        JsonArray sqlParams = new JsonArray()
                .add(heroId).add(gameId).add(health).add(mana).add(stamina).add(spawnCost)
                .add(gameId).add(health).add(mana).add(stamina).add(spawnCost)
                .add(heroId);

        conn.updateWithParams(UPSERT_HERO_CONFIG, sqlParams, cPlayer -> {
            future.complete();
        });

        return future;
    }

    private static final String INSERT_NEW_STATS_ACCOUNT = "INSERT INTO stats.account(player_id) VALUES (?)";

    private void newRosterAccount(Message<JsonObject> message) {
        final Long playerId = message.body().getLong("playerId");

        log.info("newRosterAccount playerId: " + playerId);

        conn.setAutoCommit(false, rTx -> {
            if (rTx.failed()) {
                message.fail(500, rTx.cause().getMessage());
                return;
            }

            final JsonArray sqlParams = new JsonArray().add(playerId);
            sqlParams.clear().add(playerId);

            log.info("sqlParams: " + sqlParams.encode());

            conn.updateWithParams(INSERT_NEW_STATS_ACCOUNT, sqlParams, cDevPlayer -> {
                if (cDevPlayer.failed()) {
                    message.fail(500, cDevPlayer.cause().getMessage());
                    return;
                }

                conn.commit(cRes -> {
                    if (cRes.failed()) {
                        message.fail(500, cRes.cause().getMessage());
                        return;
                    }

                    getStatsAccountQuery(playerId, message::reply);
                });
            });
        });
    }
}
