package net.badape.aresws.services;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import net.badape.aresws.EventTopic;

@Slf4j
public class HeroContentRefresh extends AbstractVerticle {

    public void start() {
        vertx.setPeriodic(config().getLong("heroRefresh", 600000L), id -> {
            // This handler will get called every second

            refreshHeroData();
        });

        EventBus eb = vertx.eventBus();
        eb.consumer(EventTopic.FORCE_HERO_REFRESH, this::messsageRefresh);
    }

    private <T> void messsageRefresh(Message<T> tMessage) {
        refreshHeroData();
    }

    private void refreshHeroData() {
        log.info("updating heroes from contentful");
        String space = config().getString("space", "wu24t0b0ngd9");
        String token = config().getString("token", "J9D_f5MjVivF9zBYd2i9Ork6ORSb7iQD0ThO5zQc0kY");

        WebClient client = WebClient.create(vertx);

        HttpRequest<Buffer> request = client.get(443, "cdn.contentful.com", "/spaces/" + space + "/entries")
                .ssl(true);
        request.addQueryParam("content_type", "hero");
        request.putHeader("Authorization", "Bearer " + token);
        request.send(result -> {
            if (result.succeeded()) {
                JsonArray heroConfig = result.result().bodyAsJsonObject().getJsonArray("items");
                JsonArray newHeroes = new JsonArray();
                heroConfig.forEach(heroObject -> {
                    JsonObject heroData = (JsonObject) heroObject;
                    newHeroes.add(heroData.getJsonObject("fields"));

                });

                vertx.eventBus().request(EventTopic.HERO_REFRESH, newHeroes, reply -> {
                    if (reply.succeeded()) {
                    } else {
                        log.info("hero update: " + reply.succeeded() + " : " + reply.cause().getMessage());
                    }
                });

            } else {
                log.error(result.cause().getMessage());
            }
        });
    }
}
