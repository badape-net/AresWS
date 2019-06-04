package net.badape.aresws.actors;

import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.badape.aresws.EventTopic;
import net.badape.aresws.db.AbstractDataVerticle;
import net.badape.aresws.db.LiquibaseVerticle;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class AccountActor extends AbstractDataVerticle {

    private EventBus eb;

    @Override
    public void start(Future<Void> startFuture) {
        schema = "players";

        DeploymentOptions liquiOpts = new DeploymentOptions()
                .setConfig(getDBConfig("account", "classpath:db/account.changelog.xml"))
                .setWorker(true);

        final List<Future> lFutures = new ArrayList<>();
        lFutures.add(liquibaseCycle(LiquibaseVerticle.class.getName(), liquiOpts));
        CompositeFuture.all(lFutures).setHandler(lRes -> {
            if (lRes.failed()) {
                startFuture.fail(lRes.cause());
            } else {
                eb = vertx.eventBus();
                eb.<JsonObject>consumer(EventTopic.GET_DEV_ACCOUNT, this::getOrCreateAccount);
                eb.<JsonObject>consumer(EventTopic.GET_DEVICE_ACCOUNT, this::getOrCreateDeviceAccount);

                getConnection(startFuture);
            }
        });
    }

    @Override
    public void stop(Future<Void> stopFuture) {
        closeClient(stopFuture);
    }

    private final static String FIND_DEV_PLAYER = "SELECT * FROM account.dev_account_view WHERE dev_account_pk = ?";
    private final static String CREATE_DEV_PLAYER = "INSERT INTO account.dev_account_view(dev_account_pk) VALUES (?)";

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

    private final static String FIND_DEVICE_PLAYER = "SELECT * FROM account.device_account_view WHERE device_account_pk = ?";
    private final static String CREATE_DEVICE_PLAYER = "INSERT INTO account.device_account_view(device_account_pk) VALUES (?)";

    private void getOrCreateDeviceAccount(Message<JsonObject> message) {

        final String deviceId = message.body().getString("deviceId");
        JsonArray sqlParams = new JsonArray().add(deviceId);

        conn.queryWithParams(FIND_DEVICE_PLAYER, sqlParams, result -> {
            if (result.succeeded() && result.result().getNumRows() != 0) {
                Long accountId = result.result().getRows().get(0).getLong("account_pk");
                JsonObject response = new JsonObject().put("accountId", accountId);
                message.reply(response);
            } else {
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
