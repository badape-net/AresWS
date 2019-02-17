package net.badape.aresws;

import io.vertx.core.*;
import lombok.extern.slf4j.Slf4j;
import net.badape.aresws.services.APIVerticle;
import net.badape.aresws.services.TCPService;

@Slf4j
public class AresWebServices extends AbstractVerticle {

    public static void main(String[] args) {

        log.info("AresWS main");

        Launcher.main(new String[]{"run", AresWebServices.class.getName(), "-ha"});
    }

    @Override
    public void start(Future<Void> future) {
        CompositeFuture.all(
                deployHelper(APIVerticle.class.getName()),
                deployHelper(TCPService.class.getName())).setHandler(result -> {
            if (result.succeeded()) {
                future.complete();
            } else {
                future.fail(result.cause());
            }
        });
    }

    private Future<Void> deployHelper(String name) {
        final Future<Void> future = Future.future();

        DeploymentOptions options = new DeploymentOptions().setConfig(config());

        vertx.deployVerticle(name, options, res -> {
            if (res.failed()) {
                log.error("Failed to deploy verticle " + name);
                future.fail(res.cause());
            } else {
                log.info("Deployed verticle " + name);
                future.complete();
            }
        });

        return future;
    }
}
