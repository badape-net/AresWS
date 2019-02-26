package net.badape.aresws.services;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.contract.RouterFactoryOptions;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;
import lombok.extern.slf4j.Slf4j;
import net.badape.aresws.EventTopic;

@Slf4j
public class APIVerticle extends AbstractVerticle {

    private HttpServer server;
    private EventBus eb;

    @Override
    public void start(Future<Void> startFuture) {
        eb = vertx.eventBus();

        OpenAPI3RouterFactory.create(this.vertx, "api.yaml", openAPI3RouterFactoryAsyncResult -> {
            if (openAPI3RouterFactoryAsyncResult.failed()) {
                // Something went wrong during router factory initialization
                Throwable exception = openAPI3RouterFactoryAsyncResult.cause();
                log.error("oops, something went wrong during factory initialization", exception);
                startFuture.fail(exception);
            }
            // Spec loaded with success
            OpenAPI3RouterFactory routerFactory = openAPI3RouterFactoryAsyncResult.result();
            // Add an handler with operationId

            routerFactory.addHandlerByOperationId("getPlayerByDevPlayerId", this::getPlayerByDevPlayerId);
            routerFactory.addFailureHandlerByOperationId("getPlayerByDevPlayerId", this::failureHandler);

            routerFactory.addHandlerByOperationId("getPlayerRoster", this::getPlayerRoster);
            routerFactory.addFailureHandlerByOperationId("getPlayerRoster", this::failureHandler);


            routerFactory.addHandlerByOperationId("heroesConfig", this::heroesConfig);

            routerFactory.addHandlerByOperationId("buyHero", this::buyHero);
            routerFactory.addFailureHandlerByOperationId("buyHero", this::failureHandler);

            // Add a security handler
            routerFactory.addSecurityHandler("api_key", this::securityHandler);

            // Before router creation you can enable/disable various router factory behaviours
            RouterFactoryOptions factoryOptions = new RouterFactoryOptions()
                    .setMountValidationFailureHandler(true) // Disable mounting of dedicated validation failure handler
                    .setMountResponseContentTypeHandler(true); // Mount ResponseContentTypeHandler automatically

            // Now you have to generate the router
            Router router = routerFactory.setOptions(factoryOptions).getRouter();

            // Now you can use your Router instance
            server = vertx.createHttpServer(getHttpServerOptions());
            server.requestHandler(router).listen((ar) -> {
                if (ar.succeeded()) {
                    log.info("Server started on port " + ar.result().actualPort());
                    startFuture.complete();
                } else {
                    log.error("oops, something went wrong during server initialization", ar.cause());
                    startFuture.fail(ar.cause());
                }
            });
        });
    }

    @Override
    public void stop(Future<Void> future) {
        server.close(result -> future.complete());
    }

    private HttpServerOptions getHttpServerOptions() {
        HttpServerOptions options = new HttpServerOptions();
        options.setHost(config().getString("http.host", "localhost"));
        options.setPort(config().getInteger("http.port", 8765));
        return options;
    }

    private void getPlayerByDevPlayerId(RoutingContext routingContext) {

        JsonObject message = new JsonObject()
                .put("playerId", Long.parseLong(routingContext.pathParam("playerId")));

        eb.<JsonObject>send(EventTopic.GET_DEV_PLAYER, message, reply -> {
            if (reply.succeeded()) {
                routingContext.response().setStatusMessage("OK").end(reply.result().body().encode());
            } else {
                routingContext.fail(reply.cause());
            }
        });

    }

    private void heroesConfig(RoutingContext routingContext) {
        JsonObject message = new JsonObject();
        eb.<JsonObject>send(EventTopic.GET_HEROES, message, reply -> {
            if (reply.succeeded()) {
                routingContext.response().setStatusMessage("OK").end(reply.result().body().encode());
            } else {
                routingContext.fail(reply.cause());
            }
        });
    }

    private void getPlayerRoster(RoutingContext routingContext) {
        JsonObject message = new JsonObject()
                .put("playerId", Long.parseLong(routingContext.pathParam("playerId")));

        log.info("getPlayerRoster: " + message.encode());

        eb.<JsonObject>send(EventTopic.GET_PLAYER_ROSTER, message, reply -> {
            if (reply.succeeded()) {
                routingContext.response().setStatusMessage("OK").end(reply.result().body().encode());
            } else {
                routingContext.fail(reply.cause());
            }
        });
    }

    private void buyHero(RoutingContext routingContext) {
        JsonObject body = routingContext.getBodyAsJson();

        log.info("buyHero");

        JsonObject message = new JsonObject()
                .put("playerId", body.getLong("playerId", null))
                .put("heroId", body.getLong("heroId", -1L));

        log.info("buyHero: " + message.encode());

        eb.<JsonObject>send(EventTopic.GET_PLAYER_ROSTER, message, reply -> {
            if (reply.succeeded()) {
                routingContext.response().setStatusMessage("OK").end(reply.result().body().encode());
            } else {
                routingContext.fail(reply.cause());
            }
        });
    }

    private void securityHandler(RoutingContext routingContext) {
        routingContext.next();
    }

    private void failureHandler(RoutingContext routingContext) {
        // This is the failure handler

        log.error("failureHandler: " + routingContext.failure().getMessage());
        Throwable failure = routingContext.failure();

        JsonObject reply = new JsonObject()
                .put("message", failure.getMessage());

        routingContext.response()
                .setStatusCode(500)
                .setStatusMessage(failure.getMessage())
                .end(reply.encode());
    }
}
