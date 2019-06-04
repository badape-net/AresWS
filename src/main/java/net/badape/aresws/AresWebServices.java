package net.badape.aresws;

import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.badape.aresws.actors.AccountActor;
import net.badape.aresws.actors.HeroStoreActor;
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
        final List<Future> sFutures = new ArrayList<>();
        sFutures.add(deployHelper(HeroStoreActor.class.getName(), 1));
        sFutures.add(deployHelper(AccountActor.class.getName(), 1));
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

    private Future<String> deployHelper(String name) {
        Future<String> future = Future.future();
        DeploymentOptions options = new DeploymentOptions().setConfig(config());
        vertx.deployVerticle(name, options, future);
        return future;
    }

    private Future<String> deployHelper(String name, int instances) {
        Future<String> future = Future.future();
        DeploymentOptions options = new DeploymentOptions().setConfig(config()).setInstances(instances);
        vertx.deployVerticle(name, options, future);
        return future;
    }

}
