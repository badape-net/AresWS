package net.badape.aresws;

import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.RequestParameters;
import io.vertx.ext.web.api.contract.RouterFactoryOptions;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class APIVerticle extends AbstractSQLVerticle {

    private final static String SQL_CREATE = "INSERT INTO players(steamId64, credits) VALUES (?, ?)";
    private final static String SQL_STEAMID = "SELECT * FROM players WHERE steamid64=?";
    private final static String SQL_ROSTER = "SELECT roster.heroId, roster FROM players " +
            "JOIN roster ON roster.playerId = players.playerId " +
            "WHERE players.steamId64 = ?";

    private final static String SQL_HERO_CREDITS = "SELECT credits, heroId FROM heroes WHERE gameId = ?";
    private final static String SQL_BUY_HERO = "UPDATE ares.players SET credits = credits - ? WHERE steamId64 = ?";

    private final static String SQL_ADD_ROSTER = "INSERT INTO ares.roster (playerId, heroId) " +
            "VALUES ((SELECT playerId FROM ares.players WHERE steamId64 = ?), ? );";

    private HttpServer server;

    public void start(Future<Void> future) {

        final String schema = "ares";
        final String changeLogFile = "classpath:db/changelog.xml";

        JsonObject sqlConfig = getDBConfig(schema, changeLogFile);

        updateSchema(sqlConfig, result -> {
            if (result.succeeded()) {
                startOpenAPI(future);
            } else {
                future.fail(result.cause());
            }
        });
    }

    private void startOpenAPI(Future<Void> future) {
        OpenAPI3RouterFactory.create(this.vertx, "api.yaml", openAPI3RouterFactoryAsyncResult -> {
            if (openAPI3RouterFactoryAsyncResult.failed()) {
                // Something went wrong during router factory initialization
                Throwable exception = openAPI3RouterFactoryAsyncResult.cause();
                log.error("oops, something went wrong during factory initialization", exception);
                future.fail(exception);
            }
            // Spec loaded with success
            OpenAPI3RouterFactory routerFactory = openAPI3RouterFactoryAsyncResult.result();
            // Add an handler with operationId

            routerFactory.addHandlerByOperationId("getPlayerBySteamId", this::getPlayerBySteamID);
            routerFactory.addFailureHandlerByOperationId("getPlayerBySteamId", this::failureHandler);

            routerFactory.addHandlerByOperationId("getPlayerRoster", this::getPlayerRoster);
            routerFactory.addFailureHandlerByOperationId("getPlayerRoster", this::failureHandler);

            routerFactory.addHandlerByOperationId("buyHero", this::buyHero);
            routerFactory.addFailureHandlerByOperationId("buyHero", this::failureHandler);

            // Add a security handler
            routerFactory.addSecurityHandler("api_key", this::securityHandler);

            // Before router creation you can enable/disable various router factory behaviours
            RouterFactoryOptions factoryOptions = new RouterFactoryOptions()
                    .setMountValidationFailureHandler(false) // Disable mounting of dedicated validation failure handler
                    .setMountResponseContentTypeHandler(true); // Mount ResponseContentTypeHandler automatically

            // Now you have to generate the router
            Router router = routerFactory.setOptions(factoryOptions).getRouter();

            // Now you can use your Router instance
            server = vertx.createHttpServer(getHttpServerOptions());
            server.requestHandler(router::accept).listen((ar) -> {
                if (ar.succeeded()) {
                    log.info("Server started on port " + ar.result().actualPort());
                    future.complete();
                } else {
                    log.error("oops, something went wrong during server initialization", ar.cause());
                    future.fail(ar.cause());
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
        options.setPort(config().getInteger("http.port", 8080));
        return options;
    }

    private void getPlayerBySteamID(RoutingContext routingContext) {

        final String steamId = routingContext.pathParam("steamId");

        log.info("getPlayerBySteamID: "+steamId );

        getConnection(routingContext, conn -> {
            JsonArray sqlParams = new JsonArray().add(steamId);
            queryWithParams(routingContext, conn, SQL_STEAMID, sqlParams, result -> {
                if (result.getNumRows() != 0) {
                    JsonObject player = result.getRows().get(0);
                    routingContext.response().setStatusMessage("OK").end(player.encode());
                } else {
                    sqlParams.add(1000);
                    updateWithParams(routingContext, conn, SQL_CREATE, sqlParams, cPlayer -> {
                        sqlParams.remove(1);
                        queryWithParams(routingContext, conn, SQL_STEAMID, sqlParams, newPlayer -> {
                            JsonObject reply = newPlayer.getRows().get(0);
                            routingContext.response().setStatusMessage("OK").end(reply.encode());
                        });
                    });
                }
            });
        });
    }

    private void getPlayerRoster(RoutingContext routingContext) {
        final String steamId = routingContext.pathParam("steamId");
        JsonArray sqlParams = new JsonArray().add(steamId);

        getConnection(routingContext, conn -> {
            queryWithParams(routingContext, conn, SQL_ROSTER, sqlParams, result -> {
                conn.close();
                JsonArray reply = new JsonArray(result.getRows());
                routingContext.response().setStatusMessage("OK").end(reply.encode());
            });
        });
    }

    private void buyHero(RoutingContext routingContext) {
        JsonObject body = routingContext.getBodyAsJson();

        final String steamId = body.getString("steamId", null);
        final Integer gameId = body.getInteger("gameId", -1);

        log.info("buyHero: "+ gameId +" : " + steamId);

        getConnection(routingContext, conn -> {
            startTx(routingContext, conn, beginTrans -> {
                JsonArray credParams = new JsonArray().add(gameId);
                querySingleWithParams(routingContext, conn, SQL_HERO_CREDITS, credParams, creditResults -> {
                    if (creditResults != null) {
                        JsonArray buyParams = new JsonArray().add(creditResults.getInteger(0)).add(steamId);
                        updateWithParams(routingContext, conn, SQL_BUY_HERO, buyParams, buyResult -> {
                            JsonArray rosterParams = new JsonArray().add(steamId).add(gameId);
                            queryWithParams(routingContext, conn, SQL_ADD_ROSTER, rosterParams, rosterResult -> {
                                endTx(routingContext, conn, commitTrans -> {
                                    rosterParams.remove(1);
                                    queryWithParams(routingContext, conn, SQL_STEAMID, rosterParams, result -> {
                                        conn.close();
                                        JsonObject reply = result.getRows().get(0);
                                        routingContext.response().setStatusMessage("OK").end(reply.encode());
                                    });
                                });
                            });
                        });
                    } else {
                        routingContext.fail(new RuntimeException("no hero found"));
                    }
                });
            });
        });
    }

    private void securityHandler(RoutingContext routingContext) {
        routingContext.next();
    }

    private void failureHandler(RoutingContext routingContext) {
        // This is the failure handler
        Throwable failure = routingContext.failure();

        log.error("failureHandler: " + routingContext.failure().getMessage());

        JsonObject reply = new JsonObject()
                .put("message", failure.getMessage());

        routingContext.response()
                .setStatusCode(500)
                .setStatusMessage(failure.getMessage())
                .end(reply.encode());
    }
}
