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

    protected JsonObject getJDBCConfig() {
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

//    protected void querySingleWithParams(String sql, JsonArray params, Handler<JsonArray> aHandler) throws RuntimeException {
//        conn.querySingleWithParams(sql, params, res -> {
//            if (res.failed()) {
//                throw new RuntimeException(res.cause());
//            }
//            aHandler.handle(res.result());
//        });
//    }
//
//    protected void update(String sql, Handler<UpdateResult> aHandler) throws RuntimeException {
//        conn.update(sql, res -> {
//            if (res.failed()) {
//                throw new RuntimeException(res.cause());
//            }
//            aHandler.handle(res.result());
//        });
//    }
//
//    protected void updateWithParams(String sql, JsonArray params, Handler<UpdateResult> aHandler) throws RuntimeException {
//        conn.updateWithParams(sql, params, res -> {
//            if (res.failed()) {
////                throw new RuntimeException(res.cause());
//            }
//            aHandler.handle(res.result());
//        });
//    }
//
//    protected void queryWithParams(String sql, JsonArray params, Handler<ResultSet> aHandler) throws RuntimeException {
//        conn.queryWithParams(sql, params, res -> {
//            if (res.failed()) {
//                throw new RuntimeException(res.cause());
//            }
//            aHandler.handle(res.result());
//        });
//    }
//
//    protected void query(String sql, Handler<ResultSet> aHandler) throws RuntimeException {
//        conn.query(sql, res -> {
//            if (res.failed()) {
//                throw new RuntimeException(res.cause());
//            }
//            aHandler.handle(res.result());
//        });
//    }
//
//    protected void startTx(Handler<Void> aHandler) throws RuntimeException {
//        conn.setAutoCommit(false, res -> {
//            if (res.failed()) {
//                throw new RuntimeException(res.cause());
//            }
//            aHandler.handle(res.result());
//        });
//    }
//
//    protected void commit(Handler<Void> aHandler) throws RuntimeException {
//        conn.commit(res -> {
//            if (res.failed()) {
//                throw new RuntimeException(res.cause());
//            }
//            aHandler.handle(res.result());
//        });
//    }
//
//    protected void rollback(Handler<Void> aHandler) throws RuntimeException {
//        conn.rollback(res -> {
//            if (res.failed()) {
//                throw new RuntimeException(res.cause());
//            }
//            aHandler.handle(res.result());
//        });
//    }
}
