package net.badape.aresws.actors;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.badape.aresws.EventTopic;
import net.badape.aresws.db.AbstractDataVerticle;
import net.badape.aresws.db.SQL;

@Slf4j
public class StoreActor extends AbstractDataVerticle {

    private EventBus eb;

    @Override
    public void start(Future<Void> startFuture) {
        schema = "store";

        eb = vertx.eventBus();

        eb.<JsonObject>consumer(EventTopic.NEW_STORE_ACCOUNT, this::newStoreAccount);
        eb.<JsonObject>consumer(EventTopic.GET_PLAYER_ROSTER, this::getPlayerRoster);
        eb.<JsonObject>consumer(EventTopic.GET_HEROES, this::getHeroes);
        eb.<JsonObject>consumer(EventTopic.BUY_HERO, this::buyHero);
        eb.<JsonObject>consumer(EventTopic.GET_CREDITS, this::getCredits);

        getConnection(result -> {
            if (result.succeeded()) {
                loadData(startFuture.completer());
            } else {
                startFuture.fail(result.cause());
            }
        });
    }

    private void getCredits(Message<JsonObject> message) {
        log.info(message.body().encode());
        getStats(message.body().getLong("playerId"), message::reply);
    }

    private void getStats(Long playerId, Handler<JsonObject> hndlr) {
        final JsonArray sqlParams = new JsonArray().add(playerId);
        conn.queryWithParams(SQL.SELECT_CREDITS, sqlParams, qRes -> {
            if (qRes.failed()) {
                hndlr.handle(new JsonObject());
            }
            hndlr.handle(qRes.result().getRows().get(0));
        });
    }

    private void newStoreAccount(Message<JsonObject> message) {
        final Long playerId = message.body().getLong("playerId");
        final JsonArray sqlParams = new JsonArray().add(playerId).add(10000);

        conn.setAutoCommit(false, rTx -> {
            if (rTx.failed()) {
                message.fail(500, rTx.cause().getMessage());
                return;
            }

            conn.updateWithParams(SQL.INSERT_NEW_ACCOUNT, sqlParams, cDevPlayer -> {
                if (cDevPlayer.failed()) {
                    message.fail(500, cDevPlayer.cause().getMessage());
                    return;
                }

                conn.commit(cRes -> {
                    if (cRes.failed()) {
                        message.fail(500, cRes.cause().getMessage());
                        return;
                    }

                    JsonObject reply = new JsonObject()
                            .put("playerId", playerId)
                            .put("credits", cDevPlayer.result().getKeys().getLong(1));

                    message.reply(reply);
                });
            });
        });

    }

    private void loadData(Handler<AsyncResult<Void>> hndlr) {

        vertx.fileSystem().readFile("config/heroes.json", result -> {
            if (result.failed()) {
                hndlr.handle(Future.failedFuture(result.cause()));
                return;
            }

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
                    conn.updateWithParams(SQL.UPSERT_HERO, sqlParams, cPlayer -> {
                        if (result.failed()) {
                            log.error("failed to update: " + heroId);
                        }
                    });
                }
            });
            hndlr.handle(Future.succeededFuture());
        });
    }

    private void buyHero(Message<JsonObject> message) {
        final Long playerId = message.body().getLong("playerId", null);
        final Long heroId = message.body().getLong("heroId", -1L);

        JsonArray buyParams = new JsonArray().add(heroId).add(playerId);
        conn.updateWithParams(SQL.UPDATE_BUY_HERO, buyParams, buyResult -> {
            if (buyResult.failed()) {
                message.fail(500, buyResult.cause().getMessage());
                return;
            }

            JsonObject jsonreply = new JsonObject().put("player_id", playerId).put("credits", 0);

            eb.<JsonObject>send(EventTopic.NEW_ROSTER_HERO, message.body(), reply -> {
                jsonreply.mergeIn(reply.result().body());
                message.reply(jsonreply);
            });
        });
    }


    private void getHeroes(Message<JsonObject> message) {

        conn.query(SQL.SQL_HEROES, result -> {
            if (result.failed()) {
                message.fail(500, result.cause().getMessage());
                return;
            }

            JsonObject hero = result.result().toJson();
            hero.remove("results");
            hero.remove("columnNames");
            hero.remove("numColumns");

            message.reply(hero);
        });
    }

    private void getPlayerRoster(Message<JsonObject> message) {

        JsonArray sqlParams = new JsonArray().add(message.body().getLong("playerId"));

        conn.queryWithParams(SQL.SELECT_ROSTER, sqlParams, result -> {
            if (result.failed()) {
                message.fail(500, result.cause().getMessage());
                return;
            }

            JsonArray reply = new JsonArray(result.result().getRows());
            message.reply(reply);
        });
    }
}
