package net.badape.aresws.services;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import lombok.extern.slf4j.Slf4j;
import net.badape.aresws.EventTopic;

@Slf4j
public class ElasticSearchClient extends AbstractVerticle {

    private WebClient client;

    public void start() {

        WebClientOptions options = new WebClientOptions()
                .setUserAgent("My-App/1.2.3");
        options.setKeepAlive(true).setDefaultHost("localhost").setDefaultPort(9200);
        client = WebClient.create(vertx, options);

        EventBus eb = vertx.eventBus();
        eb.<JsonObject>consumer(EventTopic.PUT_EVENT, this::putEvent);

    }

    private void putEvent(Message<JsonObject> jsonObjectMessage) {
        JsonObject message = jsonObjectMessage.body();
        String eventType = message.getString("eventName", "unknown.event");

        String docIndex = eventType.split("\\.")[0];

        if (docIndex.isEmpty()) {
            jsonObjectMessage.fail(500, "no event type");
        }

        log.info(message.encode());

        client.post("/" + docIndex.toLowerCase() + "/_doc").sendJsonObject(message)
                .onFailure(res -> {
                    log.error(res.getMessage());
                    jsonObjectMessage.fail(500, res.getMessage());
                })
                .onSuccess(res -> {
                    jsonObjectMessage.reply(res.bodyAsJsonObject());
                });

    }

    public void stop() {
        client.close();
    }
}
