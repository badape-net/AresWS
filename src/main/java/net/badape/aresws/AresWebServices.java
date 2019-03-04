package net.badape.aresws;

import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.badape.aresws.actors.HeroActor;
import net.badape.aresws.actors.StatsActor;
import net.badape.aresws.actors.PlayerActor;
import net.badape.aresws.actors.StoreActor;
import net.badape.aresws.db.LiquibaseVerticle;
import net.badape.aresws.services.APIVerticle;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class AresWebServices extends AbstractVerticle {

    public static void main(String[] args) {

        log.info("AresWS main");

        Launcher.main(new String[]{"run", AresWebServices.class.getName(), "-ha"});
    }

    @Override
    public void start(Future future) {

        DeploymentOptions storeOpts = new DeploymentOptions()
                .setConfig(getDBConfig("store", "classpath:db/store.changelog.xml"))
                .setWorker(true);

        DeploymentOptions playerOpts = new DeploymentOptions()
                .setConfig(getDBConfig("players", "classpath:db/player.changelog.xml"))
                .setWorker(true);

        DeploymentOptions heroRosterOpts = new DeploymentOptions()
                .setConfig(getDBConfig("stats", "classpath:db/stats.changelog.xml"))
                .setWorker(true);

        DeploymentOptions heroesOpts = new DeploymentOptions()
                .setConfig(getDBConfig("heroes", "classpath:db/heroes.changelog.xml"))
                .setWorker(true);


        final List<Future> lFutures = new ArrayList<>();
        lFutures.add(cycleHelper(LiquibaseVerticle.class.getName(), storeOpts));
        lFutures.add(cycleHelper(LiquibaseVerticle.class.getName(), playerOpts));
        lFutures.add(cycleHelper(LiquibaseVerticle.class.getName(), heroRosterOpts));
        lFutures.add(cycleHelper(LiquibaseVerticle.class.getName(), heroesOpts));

        CompositeFuture.all(lFutures).setHandler(lRes -> {
            if (lRes.failed()) {
                future.fail(lRes.cause());
            } else {
                log.info("schemas updates");
                final List<Future> sFutures = new ArrayList<>();
                sFutures.add(deployHelper(StoreActor.class.getName(), 1));
                sFutures.add(deployHelper(PlayerActor.class.getName(), 1));
                sFutures.add(deployHelper(StatsActor.class.getName(), 1));
                sFutures.add(deployHelper(HeroActor.class.getName(), 1));
                sFutures.add(deployHelper(APIVerticle.class.getName()));
//                sFutures.add(deployHelper(TCPService.class.getName()));

                CompositeFuture.join(sFutures).setHandler(result -> {
                    if (result.succeeded()) {
                        log.info("Ares Web Services running");
                        future.complete();
                    } else {
                        log.error(result.cause().getMessage());
                        future.fail(result.cause());
                    }
                });
            }
        });
    }

    private Future<Void> cycleHelper(String name, DeploymentOptions options) {
        Future<Void> future = Future.future();

        vertx.deployVerticle(name, options, res -> {
            if (res.failed()) {
                future.fail(res.cause());
            } else {
                vertx.undeploy(res.result(), future.completer());
            }
        });

        return future;
    }

    private Future<String> deployHelper(String name) {
        Future<String> future = Future.future();
        DeploymentOptions options = new DeploymentOptions().setConfig(config());
        vertx.deployVerticle(name, options, future.completer());
        return future;
    }

    private Future<String> deployHelper(String name, int instances) {
        Future<String> future = Future.future();
        DeploymentOptions options = new DeploymentOptions().setConfig(config()).setInstances(instances);
        vertx.deployVerticle(name, options, future.completer());
        return future;
    }

    private JsonObject getDBConfig(String schema, String changeLogFile) {
        JsonObject dbConfig = config().getJsonObject("db", new JsonObject());

        return new JsonObject()
                .put("url", dbConfig.getString("url", "jdbc:postgresql://localhost:5432/ares"))
                .put("user", dbConfig.getString("user", "postgres"))
                .put("password", dbConfig.getString("password", "changeme"))
                .put("driver_class", dbConfig.getString("driver_class", "org.postgresql.Driver"))
                .put("schema", schema)
                .put("changeLogFile", changeLogFile)
                .put("max_pool_size", dbConfig.getInteger("max_pool_size", 30));
    }
}
