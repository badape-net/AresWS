package net.badape.aresws.actors;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
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
public class PlayerActor extends AbstractDataVerticle {

    private EventBus eb;

    @Override
    public void start(Future<Void> startFuture) {
        schema = "players";
        eb = vertx.eventBus();
        eb.<JsonObject>consumer(EventTopic.GET_DEV_PLAYER, this::newDevPlayer);

        getConnection(startFuture.completer());
    }

    private void newDevPlayer(Message<JsonObject> message) {

        final Long devId = message.body().getLong("playerId");
        JsonArray sqlParams = new JsonArray().add(devId);

        conn.queryWithParams(SQL.SELECT_DEVID, sqlParams, result -> {
            if (result.succeeded() && result.result().getNumRows() != 0) {
                Long playerId = result.result().getRows().get(0).getLong("player_id");

                final List<Future> futures = new ArrayList<>();

                JsonObject payload = new JsonObject().put("playerId", playerId);

                futures.add(sendMessage(EventTopic.GET_CREDITS, payload));
                futures.add(sendMessage(EventTopic.GET_STATS, payload));

                CompositeFuture.all(futures).setHandler(fResult -> {
                    if (fResult.failed()) {
                        message.fail(500, fResult.cause().getMessage());
                        return;
                    }
                    JsonObject storeRes = (JsonObject) futures.get(0).result();
                    JsonObject statsRes = (JsonObject) futures.get(1).result();

                    replyProfile(playerId, storeRes, statsRes, message);

                });
            } else {
                conn.setAutoCommit(false, rTx -> {
                    if (rTx.failed()) {
                        message.fail(500, rTx.cause().getMessage());
                        return;
                    }

                    conn.update(SQL.CREATE_PLAYER, cPlayer -> {
                        if (cPlayer.failed()) {
                            message.fail(500, cPlayer.cause().getMessage());
                            return;
                        }

                        Long playerId = cPlayer.result().getKeys().getLong(0);

                        sqlParams.clear()
                                .add(devId)
                                .add(playerId);

                        conn.updateWithParams(SQL.CREATE_DEV_PLAYER, sqlParams, cDevPlayer -> {
                            if (cDevPlayer.failed()) {
                                message.fail(500, cDevPlayer.cause().getMessage());
                                return;
                            }

                            JsonObject payload = new JsonObject().put("playerId", playerId);

                            final List<Future> futures = new ArrayList<>();
                            futures.add(sendMessage(EventTopic.NEW_STORE_ACCOUNT, payload));
                            futures.add(sendMessage(EventTopic.NEW_ROSTER_ACCOUNT, payload));

                            CompositeFuture.all(futures).setHandler(fResult -> {
                                log.info("all futures complete");
                                if (fResult.failed()) {
                                    conn.rollback(rRes -> {
                                        log.error(fResult.cause().getMessage());
                                        message.fail(500, fResult.cause().getMessage());
                                    });
                                } else {
                                    conn.commit(cRes -> {
                                        final JsonObject storeRes = (JsonObject) futures.get(0).result();
                                        final JsonObject statsRes = (JsonObject) futures.get(1).result();
                                        final Long newPlayerId = cPlayer.result().getKeys().getLong(0);

                                        replyProfile(newPlayerId, storeRes, statsRes, message);
                                    });
                                }
                            });
                        });
                    });
                });
            }
        });


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
