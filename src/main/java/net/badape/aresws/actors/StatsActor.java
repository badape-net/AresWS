package net.badape.aresws.actors;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import lombok.extern.slf4j.Slf4j;
import net.badape.aresws.EventTopic;
import net.badape.aresws.db.AbstractDataVerticle;
import net.badape.aresws.db.SQL;

@Slf4j
public class StatsActor extends AbstractDataVerticle {
    private Void cRes;

    @Override
    public void start(Future<Void> startFuture) {
        sqlClient = JDBCClient.createShared(vertx, getJDBCConfig());

        EventBus eb = vertx.eventBus();

        eb.<JsonObject>consumer(EventTopic.NEW_ROSTER_ACCOUNT, this::newRosterAccount);
        eb.<JsonObject>consumer(EventTopic.GET_STATS, this::getStats);

        getConnection(rRes -> {
            startFuture.complete();
        });
    }

    private void getStats(Message<JsonObject> message) {
        getStatsQuery(message.body().getLong("playerId"), message::reply);
    }

    private void getStatsQuery(Long playerId, Handler<JsonObject> hndlr) {
        log.info("getStatsQuery: " + playerId);
        final JsonArray sqlParams = new JsonArray().add(playerId);
        queryWithParams(SQL.SELECT_STATS, sqlParams, qRes -> {
            hndlr.handle(qRes.getRows().get(0));
        });
    }

    private void loadData(Handler<AsyncResult<Void>> hndlr) {
        try {
            vertx.fileSystem().readFile("config/heroes_config.json", result -> {
                if (result.succeeded()) {
                    JsonArray heroConfig = result.result().toJsonArray();
                    startTx(rTx -> {
                        heroConfig.forEach(object -> {
                            if (object instanceof JsonObject) {
                                JsonObject hero = (JsonObject) object;
                                Long heroId = hero.getLong("hero_id");
                                Long gameId = hero.getLong("game_id");
                                Long credits = hero.getLong("credits");


                                JsonArray sqlParams = new JsonArray()
                                        .add(heroId).add(gameId).add(credits)
                                        .add(gameId).add(credits).add(heroId);

                                updateWithParams(SQL.UPSERT_HERO_CONFIG, sqlParams, cPlayer -> {
                                    if (result.failed()) {
                                        log.error("failed to update: " + heroId);
                                    }
                                });
                            }
                        });
                    });
                    hndlr.handle(Future.succeededFuture());
                } else {
                    log.error(result.cause().getMessage());
                    hndlr.handle(Future.failedFuture(result.cause()));
                }
            });
        } catch (RuntimeException e) {
            hndlr.handle(Future.failedFuture(e));
        }
    }

    private void newRosterAccount(Message<JsonObject> message) {
        final Long playerId = message.body().getLong("playerId");

        try {
            startTx(rTx -> {
                update(SQL.INSERT_NEW_STATS, cStats -> {
                    final JsonArray sqlParams = new JsonArray().add(playerId);
                    sqlParams.clear()
                            .add(playerId)
                            .add(cStats.getKeys().getLong(0));

                    log.info("sqlParams: " + sqlParams.encode());

                    updateWithParams(SQL.INSERT_NEW_STATS_ACCOUNT, sqlParams, cDevPlayer -> {
                        commit(cRes -> {
                            getStatsQuery(playerId, message::reply);
                        });
                    });
                });
            });
        } catch (RuntimeException e) {
            message.fail(500, e.getMessage());
        }

    }
}
