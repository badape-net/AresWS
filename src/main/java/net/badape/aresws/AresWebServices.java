package net.badape.aresws;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.badape.aresws.services.*;

import java.util.List;

@Slf4j
public class AresWebServices extends AbstractVerticle {

    public static void main(String[] args) {
        Launcher.main(new String[]{"run", AresWebServices.class.getName(), "-ha"});
    }

    public void start(Promise<Void> promise) {
        ConfigRetriever retriever = ConfigRetriever.create(vertx, new ConfigRetrieverOptions()
                .addStore(new ConfigStoreOptions().setType("env")
                        .setConfig(new JsonObject().put("keys", new JsonArray()
                                .add("CONTENTFUL_SPACE_ID")
                                .add("CONTENTFUL_ACCESS_TOKEN")))));

        retriever.getConfig(envConfig -> {

            final List<Future> sFutures = List.of(
//                    deployHelper(HeroContentRefresh.class.getName(), envConfig.result(), 1),
                    deployHelper(AresAnalyticsReciever.class.getName(), envConfig.result(), 1),
                    deployHelper(AresConfig.class.getName(), envConfig.result(), 1),
                    deployHelper(AresStore.class.getName(), envConfig.result(), 1),
                    deployHelper(AresAccount.class.getName(), envConfig.result(), 1),
                    deployHelper(APIVerticle.class.getName(), envConfig.result(), 1)
            );

            CompositeFuture.all(sFutures).onComplete(result -> {
                if (result.succeeded()) {
                    log.debug("Ares Web Services running");
                    promise.complete();
                } else {
                    log.error(result.cause().getMessage());
                    promise.fail(result.cause());
                }
            });
        });
    }

    private Future<String> deployHelper(String name) {
        Promise<String> promise = Promise.promise();
        DeploymentOptions options = new DeploymentOptions().setConfig(config());
        vertx.deployVerticle(name, options, promise);
        return promise.future();
    }

    private Future<String> deployHelper(String name, JsonObject envConfig, int instances) {
        Promise<String> promise = Promise.promise();
        JsonObject masterConfig = config().mergeIn(envConfig);
        DeploymentOptions options = new DeploymentOptions().setConfig(masterConfig).setInstances(instances);
        vertx.deployVerticle(name, options, promise);
        return promise.future();
    }


}
