package net.badape.aresws.services;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.ext.web.RoutingContext;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.ResourceAccessor;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@Slf4j
public abstract class AbstractSQLVerticle extends AbstractVerticle {

    protected SQLClient sqlClient;

    protected void updateSchema(JsonObject sqlConfig, Handler<AsyncResult<Void>> hndlr) {

        String dbUrl = sqlConfig.getString("url");
        String dbUser = sqlConfig.getString("user");
        String dbPassword = sqlConfig.getString("password");
        String changeLogFile = sqlConfig.getString("changeLogFile");
        String defaultSchema = sqlConfig.getString("schema", "ares").toLowerCase();

        Database db = null;
        ResourceAccessor resourceAccessor;
        try {

            JdbcConnection liquibaseDbConnection = createDatabase(dbUrl, dbUser, dbPassword, defaultSchema);
            db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(liquibaseDbConnection);
            db.setDefaultSchemaName(defaultSchema);

            resourceAccessor = new ClassLoaderResourceAccessor();
            changeLogFile = changeLogFile.substring(10);

            Liquibase liquibase = new Liquibase(changeLogFile, resourceAccessor, db);
            liquibase.update(new Contexts(), new LabelExpression());

        } catch (LiquibaseException | SQLException ex) {
            log.error(ex.getMessage());
            hndlr.handle(Future.failedFuture(ex));
        } finally {
            closeDatabase(db);

            sqlClient = JDBCClient.createShared(vertx, sqlConfig);

            hndlr.handle(Future.succeededFuture());
        }
    }

    protected JsonObject getDBConfig(String schema, String changeLogFile) {
        JsonObject dbConfig = config().getJsonObject("db", new JsonObject());

        return new JsonObject()
                .put("url", dbConfig.getString("url", "jdbc:postgresql://localhost:5432/" + schema))
                .put("user", dbConfig.getString("user", "postgres"))
                .put("password", dbConfig.getString("password", "changeme"))
                .put("driver_class", dbConfig.getString("driver_class", "org.postgresql.Driver"))
                .put("schema", schema)
                .put("changeLogFile", changeLogFile)
                .put("max_pool_size", dbConfig.getInteger("max_pool_size", 30));
    }

    /**
     * Utility method to close a liquibase database
     *
     * @param db
     */
    private void closeDatabase(Database db) {
        if (db != null) {
            try {
                db.close();
            } catch (DatabaseException e) {
                log.error("unable to close database " + db + " exception:" + e.getMessage());
            }
        }

    }

    private JdbcConnection createDatabase(String dbUrl, String dbUser, String dbPassword, String defaultSchema)
            throws SQLException, LiquibaseException {

        Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
        try (Statement statement = connection.createStatement()) {
            int count = statement.executeUpdate("CREATE SCHEMA " + defaultSchema);
        } catch (SQLException ex) {
            log.error("createDatabase "+ ex.getMessage());
        }

        return new JdbcConnection(connection);
    }

    protected void getConnection(RoutingContext routingContext,  Handler<SQLConnection> done) {
        sqlClient.getConnection(res -> {
            if (res.succeeded()) {
                done.handle(res.result());
            } else {
                routingContext.fail(res.cause());
            }
        });
    }

    protected void querySingleWithParams(RoutingContext routingContext, SQLConnection conn, String sql, JsonArray params, Handler<JsonArray> done) {
        conn.querySingleWithParams(sql, params, res -> {
            if (res.failed()) {
                conn.close();
                routingContext.fail(res.cause());
            } else {
                done.handle(res.result());
            }
        });
    }

    protected void updateWithParams(RoutingContext routingContext, SQLConnection conn, String sql, JsonArray params, Handler<UpdateResult> done) {
        conn.updateWithParams(sql, params, res -> {
            if (res.failed()) {
                conn.close();
                routingContext.fail(res.cause());
            } else {
                done.handle(res.result());
            }
        });
    }

    protected void queryWithParams(RoutingContext routingContext, SQLConnection conn, String sql, JsonArray params, Handler<ResultSet> done) {
        conn.queryWithParams(sql, params, res -> {
            if (res.failed()) {
                conn.close();
                routingContext.fail(res.cause());
            } else {
                done.handle(res.result());
            }
        });
    }

    protected void query(RoutingContext routingContext, SQLConnection conn, String sql, Handler<ResultSet> done) {
        conn.query(sql, res -> {
            if (res.failed()) {
                conn.close();
                routingContext.fail(res.cause());
            } else {
                done.handle(res.result());
            }
        });
    }

    protected void startTx(RoutingContext routingContext, SQLConnection conn, Handler<ResultSet> done) {
        conn.setAutoCommit(false, res -> {
            if (res.failed()) {
                conn.close();
                routingContext.fail(res.cause());
            } else {
                done.handle(null);
            }
        });
    }

    protected void endTx(RoutingContext routingContext, SQLConnection conn, Handler<ResultSet> done) {
        conn.commit(res -> {
            if (res.failed()) {
                conn.close();
                routingContext.fail(res.cause());
            } else {
                done.handle(null);
            }
        });
    }
}
