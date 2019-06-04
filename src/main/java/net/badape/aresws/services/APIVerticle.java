package net.badape.aresws.services;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Cookie;
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

        OpenAPI3RouterFactory.create(vertx, "api.yaml", openAPI3RouterFactoryAsyncResult -> {
            if (openAPI3RouterFactoryAsyncResult.failed()) {
                // Something went wrong during router factory initialization
                Throwable exception = openAPI3RouterFactoryAsyncResult.cause();
                log.error("oops, something went wrong during factory initialization", exception);
                startFuture.fail(exception);
            }
            // Spec loaded with success
            OpenAPI3RouterFactory routerFactory = openAPI3RouterFactoryAsyncResult.result();
            // Add an handler with operationId

            routerFactory.addHandlerByOperationId("getTeam", this::getTeam);
            routerFactory.addFailureHandlerByOperationId("getTeam", this::failureHandler);

            routerFactory.addHandlerByOperationId("getAccountRoster", this::getAccountRoster);
            routerFactory.addFailureHandlerByOperationId("getAccountRoster", this::failureHandler);

            routerFactory.addHandlerByOperationId("buyAccountRoster", this::buyAccountRoster);
            routerFactory.addFailureHandlerByOperationId("buyAccountRoster", this::failureHandler);

            // Add a security handler
            routerFactory.addSecurityHandler("platform_key", this::securityHandler);

            // Before router creation you can enable/disable various router factory behaviours
            RouterFactoryOptions factoryOptions = new RouterFactoryOptions()
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
        options.setHost(config().getString("http.host", "0.0.0.0"));
        options.setPort(config().getInteger("http.port", 8765));
        return options;
    }

    private void getTeam(RoutingContext routingContext) {
        log.info("getTeam");

        String account = routingContext.getCookie("account").getValue();
        JsonObject message = new JsonObject()
                .put("accountId", Long.parseLong(account));

        eb.<JsonObject>send(EventTopic.GET_TEAM, message, reply -> {
            if (reply.succeeded()) {
                routingContext.response().setStatusMessage("OK").end(reply.result().body().encode());
            } else {
                routingContext.fail(reply.cause());
            }
        });

    }

    private void getAccountRoster(RoutingContext routingContext) {
        log.info("getAccountRoster");
        String account = routingContext.getCookie("account").getValue();
        JsonObject message = new JsonObject()
                .put("accountId", Long.parseLong(account));

        eb.<JsonObject>send(EventTopic.GET_ROSTER, message, reply -> {
            if (reply.succeeded()) {
                routingContext.response().setStatusMessage("OK").end(reply.result().body().encode());
            } else {
                routingContext.fail(reply.cause());
            }
        });
    }

    private void buyAccountRoster(RoutingContext routingContext) {
        JsonObject body = routingContext.getBodyAsJson();
        String account = routingContext.getCookie("account").getValue();
        JsonObject message = new JsonObject()
                .put("accountId", Long.parseLong(account))
                .put("heroId", body.getLong("hero_id", -1L));

        eb.<JsonObject>send(EventTopic.BUY_HERO, message, reply -> {
            if (reply.succeeded()) {
                routingContext.response().setStatusMessage("OK").end(reply.result().body().encode());
            } else {
                routingContext.fail(reply.cause());
            }
        });
    }

    private void securityHandler(RoutingContext routingContext) {


        String platform = routingContext.request().getHeader("X-PLATFORM-KEY");

        log.info("securityHandler: "+ platform);

        JsonObject message;
        switch (platform) {
            case "dev":
                log.info("dev!");
                message = new JsonObject()
                        .put("devId", Long.parseLong(routingContext.pathParam("account")));

                eb.<JsonObject>send(EventTopic.GET_DEV_ACCOUNT, message, result -> {

                    Long accountId = result.result().body().getLong("accountId");
                    routingContext.addCookie(Cookie.cookie("account", accountId.toString()));

                    if (result.result().body().getBoolean("new", false)) {
                        JsonObject newAccount = new JsonObject().put("accountId", accountId);

                        eb.<JsonObject>send(EventTopic.NEW_ACCOUNT, newAccount, accResult -> {
                            if (accResult.failed()) {
                                routingContext.fail(500, accResult.cause());
                            } else {
                                routingContext.next();
                            }
                        });
                    } else {
                        routingContext.next();
                    }
                });
                break;
            case "device":
                log.info("device!");
                message = new JsonObject()
                        .put("deviceId", routingContext.pathParam("account"));

                eb.<JsonObject>send(EventTopic.GET_DEVICE_ACCOUNT, message, result -> {

                    log.info(result.result().body().encode());

                    Long accountId = result.result().body().getLong("accountId");
                    routingContext.addCookie(Cookie.cookie("account", accountId.toString()));

                    if (result.result().body().getBoolean("new", false)) {
                        log.info("create new store account");

                        JsonObject newAccount = new JsonObject().put("accountId", accountId);

                        eb.<JsonObject>send(EventTopic.NEW_ACCOUNT, newAccount, accResult -> {
                            if (accResult.failed()) {
                                routingContext.fail(500, accResult.cause());
                            } else {
                                routingContext.next();
                            }
                        });
                    } else {
                        routingContext.next();
                    }
                });
                break;
            default:
                routingContext.fail(403, new RuntimeException("access denied"));
        }

    }

    private void failureHandler(RoutingContext routingContext) {
        String failure = routingContext.failure().getMessage();

        log.error("failure "+ failure);

        JsonObject reply = new JsonObject()
                .put("error", failure);

        routingContext.response()
                .setStatusCode(500)
                .end(reply.encode());


    }
}
