package net.badape.aresws.services;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.*;
import lombok.extern.slf4j.Slf4j;
import net.badape.aresws.EventTopic;

@Slf4j
public class AresAccount extends AbstractVerticle {

    private Pool pool;


    public void start(Promise<Void> startPromise) {
        pool = Pool.pool(vertx, getPgConnectOptions(), getPoolOptions());
        EventBus eb = vertx.eventBus();
        eb.<JsonObject>consumer(EventTopic.GET_EOS_ACCOUNT, this::getOrCreateEOSDeviceAccount);
        startPromise.complete();
    }

    public void stop() {
        pool.close();
    }

    private final static String FIND_EOS_PLAYER = "SELECT * FROM public.eos_account_view WHERE eos_account_pk = $1";
    private final static String CREATE_EOS_PLAYER = "INSERT INTO public.eos_account_view(eos_account_pk) VALUES ($1)";

    private void getOrCreateEOSDeviceAccount(Message<JsonObject> message) {

        final String eosAccountId = message.body().getString("account_id");

        pool.preparedQuery(FIND_EOS_PLAYER).execute(Tuple.of(eosAccountId), ar -> {
            if (ar.succeeded() && ar.result().rowCount() != 0) {
                RowSet<Row> rows = ar.result();
                Row row = rows.iterator().next();
                Long accountId = row.getLong("account_pk");
                JsonObject response = new JsonObject().put("accountId", accountId);
                message.reply(response);
            } else {

                log.info("creating new accounts");
                pool.preparedQuery(CREATE_EOS_PLAYER).execute(Tuple.of(eosAccountId), cr -> {

                    if (cr.failed()) {
                        message.fail(500, cr.cause().getMessage());
                    } else {
                        pool.preparedQuery(FIND_EOS_PLAYER).execute(Tuple.of(eosAccountId), fr -> {
                            log.info(fr.result().rowCount() + "");

                            RowSet<Row> rows = fr.result();
                            Row row = rows.iterator().next();
                            Long accountId = row.getLong("account_pk");

                            JsonObject newAccount = new JsonObject().put("accountId", accountId);
                            vertx.eventBus().<JsonObject>request(EventTopic.NEW_ACCOUNT, newAccount, accResult -> {
                                if (accResult.failed()) {
                                    log.error(accResult.cause().getMessage());
                                }
                            });

                            JsonObject response = new JsonObject()
                                    .put("accountId", accountId);

                            message.reply(response);
                        });
                    }
                });
            }
        });
    }

    private PgConnectOptions getPgConnectOptions() {

        return new PgConnectOptions()
                .setPort(config().getInteger("port", 5432))
                .setHost(config().getString("host", "localhost"))
                .setDatabase(config().getString("databaseName", "aresaccount"))
                .setUser(config().getString("username", "postgres"))
                .setPassword(config().getString("password", "changeme"));
    }

    private PoolOptions getPoolOptions() {
        return new PoolOptions().setMaxSize(5);
    }
}
