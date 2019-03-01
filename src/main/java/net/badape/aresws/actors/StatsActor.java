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
import net.badape.aresws.db.SQL;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class StatsActor extends AbstractDataVerticle {

    @Override
    public void start(Future<Void> startFuture) {
        schema = "stats";
        EventBus eb = vertx.eventBus();

        eb.<JsonObject>consumer(EventTopic.NEW_ROSTER_ACCOUNT, this::newRosterAccount);
        eb.<JsonObject>consumer(EventTopic.GET_STATS, this::getStats);
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

        newRosterHeroQuery(playerId, heroId, message::reply);
    }

    private void newRosterHeroQuery(final Long playerId, final Long heroId, Handler<AsyncResult<JsonObject>> hndlr) {

        JsonArray rosterParams = new JsonArray().add(playerId).add(heroId);
        conn.setAutoCommit(false, rTx -> {
            if (rTx.failed()) {
                hndlr.handle(Future.failedFuture(rTx.cause()));
                return;
            }

            conn.updateWithParams(SQL.SQL_ADD_ROSTER, rosterParams, rosterResult -> {
                if (rosterResult.failed()) {
                    hndlr.handle(Future.failedFuture(rosterResult.cause()));
                    return;
                }

                conn.commit(rCm -> {
                    if (rCm.failed()) {
                        hndlr.handle(Future.failedFuture(rCm.cause()));
                        return;
                    }

                    rosterParams.clear();
                    rosterParams.add(playerId);
                    conn.queryWithParams(SQL.SELECT_ROSTER, rosterParams, qRes -> {
                        if (qRes.failed()) {
                            hndlr.handle(Future.failedFuture(qRes.cause()));
                            return;
                        }

                        JsonObject reply = new JsonObject().put("roster", new JsonArray(qRes.result().getRows()));
                        hndlr.handle(Future.succeededFuture(reply));
                    });
                });
            });
        });
    }

    private void getStats(Message<JsonObject> message) {
        getStatsQuery(message.body().getLong("playerId"), message::reply);
    }

    private void getStatsQuery(Long playerId, Handler<JsonObject> hndlr) {
        log.info("getStatsQuery: " + playerId);
        final JsonArray sqlParams = new JsonArray().add(playerId);
        conn.queryWithParams(SQL.SELECT_STATS, sqlParams, qRes -> {
            if (qRes.failed()) {
                hndlr.handle(new JsonObject());
                return;
            }

            hndlr.handle(qRes.result().getRows().get(0));
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

        conn.updateWithParams(SQL.UPSERT_HERO_CONFIG, sqlParams, cPlayer -> {
            future.complete();
        });

        return future;
    }

    private void newRosterAccount(Message<JsonObject> message) {
        final Long playerId = message.body().getLong("playerId");

        conn.setAutoCommit(false, rTx -> {
            if (rTx.failed()) {
                message.fail(500, rTx.cause().getMessage());
                return;
            }
            final JsonArray sqlParams = new JsonArray().add(playerId);
            sqlParams.clear().add(playerId);

            log.info("sqlParams: " + sqlParams.encode());

            conn.updateWithParams(SQL.INSERT_NEW_STATS_ACCOUNT, sqlParams, cDevPlayer -> {
                if (cDevPlayer.failed()) {
                    message.fail(500, cDevPlayer.cause().getMessage());
                    return;
                }

                conn.commit(cRes -> {
                    if (cRes.failed()) {
                        message.fail(500, cRes.cause().getMessage());
                        return;
                    }

                    getStatsQuery(playerId, message::reply);
                });
            });
        });
    }
}
