package net.badape.aresws.services;

import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import net.badape.aresws.EventTopic;
import net.badape.aresws.db.AbstractDataVerticle;

public class AresAccount extends AbstractDataVerticle {

    private final Logger log = LoggerFactory.getLogger( AresAccount.class );
    private EventBus eb;

    @Override
    public void start(Future<Void> startFuture) {

        getConnection("aresaccount", result ->{
            eb = vertx.eventBus();
            eb.<JsonObject>consumer(EventTopic.GET_DEV_ACCOUNT, this::getOrCreateAccount);
            eb.<JsonObject>consumer(EventTopic.GET_DEVICE_ACCOUNT, this::getOrCreateDeviceAccount);
        });

    }

    @Override
    public void stop(Future<Void> stopFuture) {
        closeClient(stopFuture);
    }

    private final static String FIND_DEV_PLAYER = "SELECT * FROM public.dev_account_view WHERE dev_account_pk = ?";
    private final static String CREATE_DEV_PLAYER = "INSERT INTO public.dev_account_view(dev_account_pk) VALUES (?)";

    private void getOrCreateAccount(Message<JsonObject> message) {

        final Long devId = message.body().getLong("devId");
        JsonArray sqlParams = new JsonArray().add(devId);

        conn.queryWithParams(FIND_DEV_PLAYER, sqlParams, result -> {
            if (result.succeeded() && result.result().getNumRows() != 0) {

                Long accountId = result.result().getRows().get(0).getLong("account_pk");
                JsonObject response = new JsonObject().put("accountId", accountId);
                message.reply(response);
            } else {
                conn.updateWithParams(CREATE_DEV_PLAYER, sqlParams, cPlayer -> {
                    if (cPlayer.failed()) {
                        message.fail(500, cPlayer.cause().getMessage());
                        return;
                    }

                    Long accountId = cPlayer.result().getKeys().getLong(1);
                    JsonObject response = new JsonObject()
                            .put("accountId", accountId)
                            .put("new", true);

                    message.reply(response);
                });
            }
        });
    }

    private final static String FIND_DEVICE_PLAYER = "SELECT * FROM public.device_account_view WHERE device_account_pk = ?";
    private final static String CREATE_DEVICE_PLAYER = "INSERT INTO public.device_account_view(device_account_pk) VALUES (?)";

    private void getOrCreateDeviceAccount(Message<JsonObject> message) {

        final String deviceId = message.body().getString("deviceId");
        JsonArray sqlParams = new JsonArray().add(deviceId);

        conn.queryWithParams(FIND_DEVICE_PLAYER, sqlParams, result -> {
            if (result.succeeded() && result.result().getNumRows() != 0) {
                Long accountId = result.result().getRows().get(0).getLong("account_pk");
                JsonObject response = new JsonObject().put("accountId", accountId);
                message.reply(response);
            } else {
                log.info("creating new accounts");
                conn.updateWithParams(CREATE_DEVICE_PLAYER, sqlParams, cPlayer -> {
                    if (cPlayer.failed()) {
                        message.fail(500, cPlayer.cause().getMessage());
                        return;
                    }

                    Long accountId = cPlayer.result().getKeys().getLong(1);
                    JsonObject response = new JsonObject()
                            .put("accountId", accountId)
                            .put("new", true);

                    message.reply(response);
                });
            }
        });
    }
}
