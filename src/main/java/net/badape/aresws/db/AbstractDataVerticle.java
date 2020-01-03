package net.badape.aresws.db;

import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;

public abstract class AbstractDataVerticle extends AbstractVerticle {

    private final Logger log = LoggerFactory.getLogger( AbstractDataVerticle.class );

    protected SQLClient sqlClient;
    protected SQLConnection conn;
    protected String schema = "public";

    protected Future<UpdateResult> sqlTransaction(final String sqlStatement, JsonArray sqlParams) {
        Promise<UpdateResult> promise = Promise.promise();

        conn.setAutoCommit(false, rTx -> {
            if (rTx.failed()) {
                log.error(rTx.cause().getMessage());
                promise.fail(rTx.cause());
            }

            conn.updateWithParams(sqlStatement, sqlParams, updateResult -> {
                if (updateResult.failed()) {
                    log.error(updateResult.cause().getMessage());
                    promise.fail(updateResult.cause());
                }

                conn.commit(commitResult -> {
                    if (commitResult.failed()) {
                        log.error(commitResult.cause().getMessage());
                        promise.fail(commitResult.cause());
                    }
                    promise.complete(updateResult.result());
                });
            });
        });
        return promise.future();
    }

    protected void getConnection(String dbName, Handler<AsyncResult<Void>> hndlr) {
        sqlClient = JDBCClient.createNonShared(vertx, getJDBCConfig(dbName));

        sqlClient.getConnection(res -> {
            if (res.failed()) {
                hndlr.handle(Future.failedFuture(res.cause()));
                return;
            }
            conn = res.result();
            hndlr.handle(Future.succeededFuture());
        });
    }

    protected void closeClient(Future<Void> stopFuture) {
        conn.close();
    }

    protected Future<JsonObject> sendMessage(final String topic, final JsonObject message) {
        Promise<JsonObject> promise = Promise.promise();

        vertx.eventBus().<JsonObject>request(topic, message, reply -> {
            if (reply.failed()) {
                promise.fail(reply.cause());
            } else {
                promise.complete(reply.result().body());
            }
        });

        return promise.future();
    }

    private JsonObject getJDBCConfig(String dbName) {
        JsonObject dbConfig = config().getJsonObject("db", new JsonObject());

        return new JsonObject()
                .put("url", dbConfig.getString("url", "jdbc:postgresql://localhost:5432/"+ dbName))
                .put("user", dbConfig.getString("user", "postgres"))
                .put("password", dbConfig.getString("password", "changeme"))
                .put("driver_class", dbConfig.getString("driver_class", "org.postgresql.Driver"))
                .put("max_pool_size", dbConfig.getInteger("max_pool_size", 10));
    }
}
