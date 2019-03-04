package net.badape.aresws.db;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractDataVerticle extends AbstractVerticle {

    protected SQLConnection conn;
    protected String schema = "public";

    private JsonObject getJDBCConfig() {
        JsonObject dbConfig = config().getJsonObject("db", new JsonObject());

        return new JsonObject()
                .put("url", dbConfig.getString("url", "jdbc:postgresql://localhost:5432/ares"))
                .put("user", dbConfig.getString("user", "postgres"))
                .put("password", dbConfig.getString("password", "changeme"))
                .put("driver_class", dbConfig.getString("driver_class", "org.postgresql.Driver"))
                .put("schema", schema)
                .put("max_pool_size", dbConfig.getInteger("max_pool_size", 30));
    }

    protected void getConnection(Handler<AsyncResult<Void>> hndlr) {
        SQLClient sqlClient = JDBCClient.createShared(vertx, getJDBCConfig());

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
        Future<JsonObject> future = Future.future();

        vertx.eventBus().<JsonObject>send(topic, message, reply -> {
            if (reply.failed()) {
                future.fail(reply.cause());
            } else {
                future.complete(reply.result().body());
            }
        });

        return future;
    }
}
