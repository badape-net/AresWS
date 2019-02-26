package net.badape.aresws.db;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractDataVerticle extends AbstractVerticle {

    protected SQLClient sqlClient;
    protected SQLConnection connection;

    protected JsonObject getJDBCConfig() {
        JsonObject dbConfig = config().getJsonObject("db", new JsonObject());

        return new JsonObject()
                .put("url", dbConfig.getString("url", "jdbc:postgresql://localhost:5432/ares"))
                .put("user", dbConfig.getString("user", "postgres"))
                .put("password", dbConfig.getString("password", "changeme"))
                .put("driver_class", dbConfig.getString("driver_class", "org.postgresql.Driver"))
                .put("schema", "ares")
                .put("max_pool_size", dbConfig.getInteger("max_pool_size", 30));
    }

    protected void getConnection(Handler<Void> aHandler) {
        sqlClient.getConnection(res -> {
            if (res.failed()) {
                throw new RuntimeException(res.cause());
            }
            connection = res.result();
            aHandler.handle(null);
        });
    }

    protected void querySingleWithParams(String sql, JsonArray params, Handler<JsonArray> aHandler) {
        connection.querySingleWithParams(sql, params, res -> {
            if (res.failed()) {
                throw new RuntimeException(res.cause());
            }
            aHandler.handle(res.result());
        });
    }

    protected void update(String sql, Handler<UpdateResult> aHandler) {
        connection.update(sql, res -> {
            if (res.failed()) {
                log.error(res.cause().getMessage());
                throw new RuntimeException(res.cause());
            }
            aHandler.handle(res.result());
        });
    }

    protected void updateWithParams(String sql, JsonArray params, Handler<UpdateResult> aHandler) {
        connection.updateWithParams(sql, params, res -> {
            if (res.failed()) {
                log.error(res.cause().getMessage());
                throw new RuntimeException(res.cause());
            }
            aHandler.handle(res.result());
        });
    }

    protected void queryWithParams(String sql, JsonArray params, Handler<ResultSet> aHandler) {
        connection.queryWithParams(sql, params, res -> {
            if (res.failed()) {
                log.error(res.cause().getMessage());
                throw new RuntimeException(res.cause());
            }
            aHandler.handle(res.result());
        });
    }

    protected void query(String sql, Handler<ResultSet> aHandler) {
        connection.query(sql, res -> {
            if (res.failed()) {
                throw new RuntimeException(res.cause());
            }
            aHandler.handle(res.result());
        });
    }

    protected void startTx(Handler<Void> aHandler) {
        connection.setAutoCommit(false, res -> {
            if (res.failed()) {
                throw new RuntimeException(res.cause());
            }
            aHandler.handle(null);
        });
    }

    protected void commit(Handler<Void> aHandler) {
        connection.commit(res -> {
            if (res.failed()) {
                throw new RuntimeException(res.cause());
            }
            aHandler.handle(null);
        });
    }

    protected void rollback(Handler<Void> aHandler) {
        connection.rollback(res -> {
            if (res.failed()) {
                throw new RuntimeException(res.cause());
            }
            aHandler.handle(null);
        });
    }
}
