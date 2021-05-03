package net.badape.aresws.services;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import io.vertx.ext.web.openapi.RouterBuilderOptions;
import lombok.extern.slf4j.Slf4j;
import net.badape.aresws.EventTopic;

@Slf4j
public class APIVerticle extends AbstractVerticle {

    private HttpServer server;
    private EventBus eb;

    public void start(Promise<Void> startPromise) {
        eb = vertx.eventBus();

        RouterBuilder.create(vertx, "api.yaml").onFailure(Throwable::printStackTrace).onSuccess(routerBuilder -> {
// Before router creation you can enable/disable various router factory behaviours
            RouterBuilderOptions factoryOptions = new RouterBuilderOptions()
                    .setMountResponseContentTypeHandler(true); // Mount ResponseContentTypeHandler automatically
            routerBuilder.setOptions(factoryOptions);

            routerBuilder.operation("get-roster").handler(this::getRoster).failureHandler(this::failureHandler).failureHandler(this::failureHandler);
            routerBuilder.operation("post-roster").handler(this::postRoster).failureHandler(this::failureHandler);

            routerBuilder.operation("getGameNews").handler(this::getGameNews).failureHandler(this::failureHandler);
            routerBuilder.operation("get-health").handler(this::getHealth).failureHandler(this::failureHandler);

            routerBuilder.operation("getCharacterConfig").handler(this::getCharacterConfig).failureHandler(this::failureHandler);

            routerBuilder.rootHandler(this::rootHandler);

            Router router = routerBuilder.createRouter();

            // Now you can use your Router instance
            server = vertx
                    .createHttpServer(getHttpServerOptions())
                    .requestHandler(router);
            server.listen()
                    .onSuccess(server -> System.out.println("Server started on port " + server.actualPort()))
                    .onFailure(Throwable::printStackTrace);
        });
    }

    private void getCharacterConfig(RoutingContext routingContext) {
        JsonObject message = new JsonObject();
        eb.<JsonArray>request(EventTopic.GET_CHARACTERS_CONFIG, message, reply -> {
            if (reply.succeeded()) {
                routingContext.response().setStatusMessage("OK").end(reply.result().body().encode());
            } else {
                routingContext.fail(reply.cause());
            }
        });
    }

    private void rootHandler(RoutingContext routingContext) {
        routingContext.next();
    }

    private void getHealth(RoutingContext routingContext) {
        routingContext.response().setStatusMessage("OK").end(new JsonObject().put("id", "OK").encode());
    }

    public void stop(Promise<Void> startPromise) {
        server.close(result -> startPromise.complete());
    }

    private HttpServerOptions getHttpServerOptions() {
        HttpServerOptions options = new HttpServerOptions();
        options.setHost(config().getString("http.host", "0.0.0.0"));
        options.setPort(config().getInteger("http.port", 8765));
        return options;
    }

    private void getRoster(RoutingContext routingContext) {

        JsonObject message = new JsonObject()
                .put("account_id", routingContext.pathParam("account"));

        eb.<JsonObject>request(EventTopic.GET_EOS_ACCOUNT, message, result -> {

            Long aresAccountId = result.result().body().getLong("accountId");
            JsonObject aMessage = new JsonObject().put("accountId", aresAccountId);

            eb.<JsonObject>request(EventTopic.GET_TEAM, aMessage, reply -> {
                if (reply.succeeded()) {
                    routingContext.response().setStatusMessage("OK").end(reply.result().body().encode());
                } else {
                    routingContext.fail(reply.cause());
                }
            });
        });
    }

    private void getAccountRoster(RoutingContext routingContext) {
        String eosAccountId = routingContext.pathParam("account");

        if (eosAccountId == null) {
            routingContext.fail(new Throwable("missing eos id"));
        }

        JsonObject message = new JsonObject().put("account_id", eosAccountId);

        eb.<JsonObject>request(EventTopic.GET_EOS_ACCOUNT, message, result -> {

            if (result.succeeded()) {
                Long aresAccountId = result.result().body().getLong("accountId");
                JsonObject aMessage = new JsonObject().put("accountId", aresAccountId);

                eb.<JsonArray>request(EventTopic.GET_ROSTER, aMessage, reply -> {
                    if (reply.succeeded()) {
                        routingContext.response().setStatusMessage("OK").end(reply.result().body().encode());
                    } else {
                        routingContext.fail(reply.cause());
                    }
                });
            } else {
                routingContext.fail(result.cause());
            }
        });
    }

    private void postRoster(RoutingContext routingContext) {
        String eosAccountId = routingContext.pathParam("account");

        if (eosAccountId == null) {
            routingContext.fail(new Throwable("missing eos id"));
        }

        JsonObject body = routingContext.getBodyAsJson();

        String name = body.getString("name", "Empty");
        if (name.equals("Empty")) {
            routingContext.fail(new Throwable("missing hero name"));
        }

        JsonObject message = new JsonObject().put("account_id", eosAccountId);
        eb.<JsonObject>request(EventTopic.GET_EOS_ACCOUNT, message, result -> {

            Long aresAccountId = result.result().body().getLong("accountId");
            JsonObject bMessage = new JsonObject()
                    .put("accountId", aresAccountId)
                    .put("title", name);

            eb.<JsonObject>request(EventTopic.BUY_HERO, bMessage, reply -> {
                if (reply.succeeded()) {
                    log.info(reply.result().body().encode());
                    routingContext.response().setStatusMessage("OK").end(reply.result().body().encode());
                } else {
                    routingContext.fail(reply.cause());
                }
            });
        });
    }

    private void getGameNews(RoutingContext routingContext) {

        log.error("GetGameNews!");
        JsonObject message = new JsonObject();

        routingContext.response().setStatusMessage("OK").end("{}");
    }

    private void failureHandler(RoutingContext routingContext) {
        String failure = routingContext.failure().getMessage();

        log.error("failure: " + failure);

        JsonObject reply = new JsonObject().put("error", failure);
        routingContext.response().setStatusCode(500).end(reply.encode());
    }
}
