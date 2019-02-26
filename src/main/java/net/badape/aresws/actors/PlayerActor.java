package net.badape.aresws.actors;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import lombok.extern.slf4j.Slf4j;
import net.badape.aresws.EventTopic;
import net.badape.aresws.db.AbstractDataVerticle;
import net.badape.aresws.db.SQL;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class PlayerActor extends AbstractDataVerticle {

    private EventBus eb;

    @Override
    public void start(Future<Void> startFuture) {

        sqlClient = JDBCClient.createShared(vertx, getJDBCConfig());

        eb = vertx.eventBus();

        eb.<JsonObject>consumer(EventTopic.GET_DEV_PLAYER, this::newDevPlayer);

        getConnection(rRes -> {
            startFuture.complete();
        });
    }

//    private void getDevPlayer(Message<JsonObject> message) {
//        final Long steamId = message.body()
//                .getLong("playerId");
//        JsonArray sqlParams = new JsonArray().add(steamId);
//
//        queryWithParams(SQL.SQL_ROSTER, sqlParams, result -> {
//
//            JsonArray reply = new JsonArray(result.getRows());
//            message.reply(reply);
//        });
//    }

    private void newDevPlayer(Message<JsonObject> message) {
        try {
            final Long playerId = message.body().getLong("playerId");

            JsonArray sqlParams = new JsonArray().add(playerId);

            queryWithParams(SQL.SELECT_DEVID, sqlParams, result -> {

                if (result.getNumRows() != 0) {
                    JsonObject player = result.getRows().get(0);
                    final List<Future> futures = new ArrayList<>();

                    futures.add(sendMessage(EventTopic.GET_CREDITS, message.body()));
                    futures.add(sendMessage(EventTopic.GET_STATS, message.body()));

                    CompositeFuture.all(futures).setHandler(fResult -> {
                        if (fResult.failed()) {
                            message.fail(500, fResult.cause().getMessage());
                        } else {
                            JsonObject storeRes = (JsonObject) futures.get(0).result();
                            JsonObject statsRes = (JsonObject) futures.get(1).result();

                            replyProfile(playerId, storeRes, statsRes, message);
                        }
                    });
                } else {
                    startTx(rTx -> {
                        update(SQL.CREATE_PLAYER, cPlayer -> {
                            sqlParams.clear()
                                    .add(playerId)
                                    .add(cPlayer.getKeys().getLong(0));

                            updateWithParams(SQL.CREATE_DEV_PLAYER, sqlParams, cDevPlayer -> {
                                final List<Future> futures = new ArrayList<>();
                                futures.add(sendMessage(EventTopic.NEW_STORE_ACCOUNT, message.body()));
                                futures.add(sendMessage(EventTopic.NEW_ROSTER_ACCOUNT, message.body()));

                                CompositeFuture.all(futures).setHandler(fResult -> {
                                    log.info("all futures complete");
                                    if (fResult.failed()) {
                                        rollback(rRes -> {
                                            log.error(fResult.cause().getMessage());
                                            message.fail(500, fResult.cause().getMessage());
                                        });
                                    } else {
                                        commit(cRes -> {
                                            final JsonObject storeRes = (JsonObject) futures.get(0).result();
                                            final JsonObject statsRes = (JsonObject) futures.get(1).result();
                                            final Long newPlayerId = cPlayer.getKeys().getLong(0);

                                            replyProfile(newPlayerId, storeRes, statsRes, message);
                                        });
                                    }
                                });
                            });
                        });
                    });
                }
            });

        } catch (Exception e) {
            log.error(e.getMessage());
            message.fail(e.hashCode(), e.getMessage());
        }
    }

    private void replyProfile(final Long playerId, final JsonObject storeRes, final JsonObject statsRes,
                              final Message<JsonObject> message) {

        JsonObject reply = new JsonObject()
                .put("playerId", playerId)
                .put("credits", storeRes.getLong("credits"))
                .put("stats", statsRes);

        message.reply(reply);
    }

    private Future<JsonObject> sendMessage(final String topic, final JsonObject message) {
        Future<JsonObject> future = Future.future();

        eb.<JsonObject>send(topic, message, reply -> {
            if (reply.failed()) {
                future.fail(reply.cause());
            } else {
                future.complete(reply.result().body());
            }
        });

        return future;
    }

}
