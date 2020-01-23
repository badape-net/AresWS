package net.badape.aresws.services;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import net.badape.aresws.EventTopic;
import net.badape.aresws.db.AbstractDataVerticle;

import java.util.ArrayList;
import java.util.List;

public class AresContent extends AbstractDataVerticle {

    private final Logger log = LoggerFactory.getLogger( AresContent.class );
    private EventBus eb;
    private WebClient webClient;
    private String space;
    private String token;

    @Override
    public void start(Future<Void> startFuture) {

        webClient = WebClient.create(vertx);
        space = config().getString("CONTENTFUL_SPACE_ID");
        token = config().getString("CONTENTFUL_ACCESS_TOKEN");

        getConnection("aresaccount", result ->{
            eb = vertx.eventBus();
            eb.<JsonObject>consumer(EventTopic.GET_GAME_NEWS, this::getGameNews);
        });

    }

    private void getGameNews(Message<JsonObject> message) {

        HttpRequest<Buffer> request = webClient.get(443, "cdn.contentful.com", "/spaces/" + space + "/entries")
                .ssl(true);
        request.addQueryParam("content_type", "news");
        request.putHeader("Authorization", "Bearer " + token);
        request.send(result -> {
        if (result.succeeded()) {
            message.reply(result.result().bodyAsJsonObject());
        } else {
            log.error(result.cause().getMessage());
            message.fail(500,result.cause().getMessage());
        }
    });

    }
}
