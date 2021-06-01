package net.badape.aresws.services;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import net.badape.aresws.EventTopic;

public class AresConfig extends AbstractVerticle {

    private Pool pool;

    public void start() {
        pool = Pool.pool(vertx, getPgConnectOptions(), getPoolOptions());
        EventBus eb = vertx.eventBus();
        eb.<JsonArray>consumer(EventTopic.GET_FACTION_CONFIG, this::getFactionConfig);
        eb.<JsonArray>consumer(EventTopic.GET_CHARACTERS_CONFIG, this::getCharactersConfig);
    }

    private static final String SELECT_CONFIG =
            "SELECT * FROM public.character_config_view";

    private void getCharactersConfig(Message<JsonArray> message) {
        pool.query(SELECT_CONFIG).execute(ar -> {
            if (ar.succeeded()) {
                JsonArray reply = new JsonArray();
                ar.result().forEach(row ->{
                    reply.add(row.toJson());
                });
                message.reply(reply);

            } else {
                message.fail(500, ar.cause().getMessage());
            }
        });
    }

    private void getFactionConfig(Message<JsonArray> jsonArrayMessage) {
        JsonArray reply = new JsonArray();

        jsonArrayMessage.reply(reply);
    }

    private PgConnectOptions getPgConnectOptions() {

        return new PgConnectOptions()
                .setPort(config().getInteger("port", 5432))
                .setHost(config().getString("host", "postgres"))
                .setDatabase(config().getString("databaseName", "aresconfig"))
                .setUser(config().getString("username", "postgres"))
                .setPassword(config().getString("password", "changeme"));
    }

    private PoolOptions getPoolOptions() {
        return new PoolOptions().setMaxSize(5);
    }
}
