package net.badape.aresws.actors;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import lombok.extern.slf4j.Slf4j;
import net.badape.aresws.EventTopic;
import net.badape.aresws.db.AbstractDataVerticle;
import net.badape.aresws.db.SQL;

@Slf4j
public class StoreActor extends AbstractDataVerticle {

    @Override
    public void start(Future<Void> startFuture) {

        sqlClient = JDBCClient.createShared(vertx, getJDBCConfig());

        EventBus eb = vertx.eventBus();

        eb.<JsonObject>consumer(EventTopic.NEW_STORE_ACCOUNT, this::newStoreAccount);
        eb.<JsonObject>consumer(EventTopic.GET_PLAYER_ROSTER, this::getPlayerRoster);
        eb.<JsonObject>consumer(EventTopic.GET_HEROES, this::getHeroes);
        eb.<JsonObject>consumer(EventTopic.BUY_HERO, this::buyHero);
        eb.<JsonObject>consumer(EventTopic.GET_CREDITS, this::getCredits);

        getConnection(rRes -> {
            loadData(startFuture.completer());
        });
    }

    private void getCredits(Message<JsonObject> message) {
        log.info(message.body().encode());
        getStats(message.body().getLong("playerId"), message::reply);
    }

    private void getStats(Long playerId, Handler<JsonObject> hndlr) {
        final JsonArray sqlParams = new JsonArray().add(playerId);
        queryWithParams(SQL.SELECT_CREDITS, sqlParams, qRes -> {
            hndlr.handle(qRes.getRows().get(0));
        });
    }

    private void newStoreAccount(Message<JsonObject> message) {
        final Long playerId = message.body().getLong("playerId");

        final JsonArray sqlParams = new JsonArray().add(playerId);


        sqlParams.clear()
                .add(playerId)
                .add(10000);

        try {
            startTx(rTx -> {
                updateWithParams(SQL.INSERT_NEW_ACCOUNT, sqlParams, cDevPlayer -> {
                    commit(cRes -> {
                        JsonObject reply = new JsonObject()
                                .put("playerId", playerId)
                                .put("credits", cDevPlayer.getKeys().getLong(1));

                        message.reply(reply);
                    });
                });
            });
        } catch (RuntimeException e) {
            log.error(e.getMessage());
            message.fail(500, e.getMessage());
        }
    }

    private void loadData(Handler<AsyncResult<Void>> hndlr) {
        try {
            vertx.fileSystem().readFile("config/heroes.json", result -> {
                if (result.succeeded()) {
                    JsonArray heroConfig = result.result().toJsonArray();
                    heroConfig.forEach(object -> {
                        if (object instanceof JsonObject) {
                            JsonObject hero = (JsonObject) object;
                            Long heroId = hero.getLong("hero_id");
                            Long gameId = hero.getLong("game_id");
                            Long credits = hero.getLong("credits");
                            String description = hero.getString("description");
                            JsonArray sqlParams = new JsonArray()
                                    .add(heroId).add(gameId).add(credits).add(description)
                                    .add(gameId).add(credits).add(description).add(heroId);
                            updateWithParams(SQL.UPSERT_HERO, sqlParams, cPlayer -> {
                                if (result.failed()) {
                                    log.error("failed to update: " + heroId);
                                }
                            });
                        }
                    });
                    hndlr.handle(Future.succeededFuture());
                } else {
                    log.error(result.cause().getMessage());
                    hndlr.handle(Future.failedFuture(result.cause()));
                }
            });
        } catch (RuntimeException e) {
            hndlr.handle(Future.failedFuture(e));
        }
    }

    private void buyHero(Message<JsonObject> message) {
        final Long playerId = message.body().getLong("playerId", null);
        final Long heroId = message.body().getLong("heroId", -1L);
        try {
            startTx(beginTrans -> {
                JsonArray credParams = new JsonArray().add(heroId);
                querySingleWithParams(SQL.SELECT_HERO_CREDITS, credParams, creditResults -> {
                    if (creditResults != null) {
                        JsonArray buyParams = new JsonArray().add(creditResults.getLong(0)).add(playerId);
                        updateWithParams(SQL.UPDATE_BUY_HERO, buyParams, buyResult -> {
                            JsonArray rosterParams = new JsonArray().add(playerId).add(heroId);
                            queryWithParams(SQL.SQL_ADD_ROSTER, rosterParams, rosterResult -> {
                                commit(commitTrans -> {
                                    rosterParams.remove(1);
                                    queryWithParams(SQL.SELECT_PLAYER, rosterParams, result -> {

                                        JsonObject player = result.getRows().get(0);
                                        JsonObject jsonreply = new JsonObject()
                                                .put("player_id", player.getLong("player_id"))
                                                .put("credits", player.getLong("credits"));

                                        message.reply(jsonreply);
                                    });
                                });
                            });
                        });
                    }
                });
            });
        } catch (RuntimeException e) {
            message.reply(new JsonObject().put("message", e.getMessage()));
        }
    }

    private void getHeroes(Message<JsonObject> message) {
        try {
            query(SQL.SQL_HEROES, result -> {

                JsonObject hero = result.toJson();
                hero.remove("results");
                hero.remove("columnNames");
                hero.remove("numColumns");
                log.info(hero.encode());
                message.reply(hero);
            });
        } catch (RuntimeException e) {
            message.reply(new JsonObject().put("message", e.getMessage()));
        }

    }

    private void getPlayerRoster(Message<JsonObject> message) {

        try {
            JsonArray sqlParams = new JsonArray().add(message.body().getLong("playerId"));

            queryWithParams(SQL.SQL_ROSTER, sqlParams, result -> {

                JsonArray reply = new JsonArray(result.getRows());
                message.reply(reply);
            });

        } catch (RuntimeException e) {
            message.reply(new JsonObject().put("message", e.getMessage()));
        }
    }


}
