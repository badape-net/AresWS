package net.badape.aresws.actors;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.badape.aresws.EventTopic;
import net.badape.aresws.db.AbstractDataVerticle;


import java.util.ArrayList;
import java.util.List;

@Slf4j
public class HeroActor extends AbstractDataVerticle {

    private EventBus eb;

    @Override
    public void start(Future<Void> startFuture) {
        schema = "heroes";

        eb = vertx.eventBus();
        eb.<JsonObject>consumer(EventTopic.GET_HEROES_DATA, this::getHeroData);
        eb.<Long>consumer(EventTopic.GET_HERO_CREDITS, this::getHeroCredits);
        getConnection(result -> {
            if (result.succeeded()) {
                loadData(startFuture.completer());
            } else {
                startFuture.fail(result.cause());
            }
        });
    }

    private final static String SELECT_HERO_CREDITS = "SELECT credits FROM heroes.hero WHERE hero_id = ?";

    private void getHeroCredits(Message<Long> message) {
        Long heroId = message.body();
        final JsonArray sqlParams = new JsonArray().add(heroId);

        conn.querySingleWithParams(SELECT_HERO_CREDITS, sqlParams, row -> {
            if (row.failed()) {
                message.fail(500, row.cause().getMessage());
                return;
            }
            message.reply(row.result().getLong(0));
        });
    }

    private static final String SQL_HEROES = "SELECT * FROM heroes.hero;";

    private void getHeroData(Message<JsonObject> message) {

        conn.query(SQL_HEROES, result -> {
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

    private void loadData(Handler<AsyncResult<Void>> hndlr) {

        vertx.fileSystem().readFile("config/heroes.json", result -> {
            if (result.failed()) {
                log.error(result.cause().getMessage());
                hndlr.handle(Future.failedFuture(result.cause()));
                return;
            }

            JsonArray heroConfig = result.result().toJsonArray();

            final List<Future> lFutures = new ArrayList<>();

            conn.setAutoCommit(false, rTx -> {
                if (rTx.failed()) {
                    hndlr.handle(Future.failedFuture(rTx.cause()));
                    return;
                }

                heroConfig.forEach(object -> {
                    if (object instanceof JsonObject) {
                        JsonObject hero = (JsonObject) object;
                        lFutures.add(writeHero(hero));
                    }
                });

                CompositeFuture.all(lFutures).setHandler(lRes -> {
                    if (lRes.failed()) {
                        conn.rollback(rbRes -> {
                            hndlr.handle(Future.failedFuture(lRes.cause()));
                        });

                        return;
                    }

                    conn.commit(cRes -> {
                        if (cRes.failed()) {
                            hndlr.handle(Future.failedFuture(cRes.cause()));
                            return;
                        }
                        hndlr.handle(Future.succeededFuture());
                    });

                });
            });
        });
    }

    private static final String UPSERT_HEROES_CONFIG =
            "INSERT INTO heroes.hero(hero_id, game_id, credits, description, health, mana, stamina, spawn_cost) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT ON CONSTRAINT hero_pkey DO " +
                    "UPDATE SET game_id=?, credits=?, description=?, health=?, mana=?, stamina=?, spawn_cost=? " +
                    "WHERE hero.hero_id=?";

    private Future writeHero(JsonObject hero) {
        Future<Void> future = Future.future();

        Long heroId = hero.getLong("hero_id");
        Long gameId = hero.getLong("game_id");
        Long credits = hero.getLong("credits");
        String description = hero.getString("description");
        Long health = hero.getLong("health");
        Long mana = hero.getLong("mana");
        Long stamina = hero.getLong("stamina");
        Long spawnCost = hero.getLong("spawn_cost");


        JsonArray sqlParams = new JsonArray()
                .add(heroId).add(gameId).add(credits).add(description).add(health).add(mana).add(stamina).add(spawnCost)
                .add(gameId).add(credits).add(description).add(health).add(mana).add(stamina).add(spawnCost)
                .add(heroId);

        conn.updateWithParams(UPSERT_HEROES_CONFIG, sqlParams, cPlayer -> {
            if (cPlayer.failed()) {
                future.fail(cPlayer.cause());
                return;
            }
            future.complete();
        });

        return future;
    }
}
