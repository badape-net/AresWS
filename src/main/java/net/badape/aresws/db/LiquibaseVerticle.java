package net.badape.aresws.db;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
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
public class LiquibaseVerticle extends AbstractVerticle {

    private Database db;

    @Override
    public void start(Future<Void> startFuture) {
        log.info("starting schema updates");

        String dbUrl = config().getString("url");
        String dbUser = config().getString("user");
        String dbPassword = config().getString("password");
        String changeLogFile = config().getString("changeLogFile");
        String defaultSchema = config().getString("schema", "ares").toLowerCase();


        try {

            JdbcConnection liquibaseDbConnection = createSchema(dbUrl, dbUser, dbPassword, defaultSchema);
            db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(liquibaseDbConnection);
            db.setDefaultSchemaName(defaultSchema);

            ResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor();
            changeLogFile = changeLogFile.substring(10);

            Liquibase liquibase = new Liquibase(changeLogFile, resourceAccessor, db);
            liquibase.update(new Contexts(), new LabelExpression());

            startFuture.complete();
        } catch (LiquibaseException | SQLException ex) {
            log.error(ex.getMessage());
            startFuture.fail(ex.getCause());
        } finally {
            closeDatabase();
            log.info("completing schema updates");

        }
    }

    private JdbcConnection createSchema(String dbUrl, String dbUser, String dbPassword, String defaultSchema)
            throws SQLException {

        Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
        try (Statement statement = connection.createStatement()) {
            int count = statement.executeUpdate("CREATE SCHEMA " + defaultSchema);
        } catch (SQLException ex) {
            log.error("createSchema " + ex.getMessage());
        }

        return new JdbcConnection(connection);
    }

    private void closeDatabase() {
        if (db != null) try {
            log.info("closing db");
            db.close();
        } catch (DatabaseException e) {
            log.error("unable to close database " + db + " exception:" + e.getMessage());
        } finally {
            log.info("closed");
        }

    }
}
