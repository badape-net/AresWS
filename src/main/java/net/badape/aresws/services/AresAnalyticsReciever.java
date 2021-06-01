package net.badape.aresws.services;


import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.parsetools.JsonEventType;
import io.vertx.core.parsetools.JsonParser;
import lombok.extern.slf4j.Slf4j;
import net.badape.aresws.EventTopic;

@Slf4j
public class AresAnalyticsReciever extends AbstractVerticle {

    public void start(Promise<Void> startPromise) {
        NetServerOptions options = new NetServerOptions().setPort(6543);
        NetServer server = vertx.createNetServer(options);
        EventBus eb = vertx.eventBus();

        server.connectHandler(socket -> {

            log.info("connection from: " + socket.remoteAddress().host());
            JsonParser parser = JsonParser.newParser();

            parser.objectValueMode();
            parser.exceptionHandler(err -> {
                log.info(err.getMessage());
            });
            parser.handler(event -> {
                if (event.type() == JsonEventType.VALUE) {
                    if (event.isObject()) {
                        eb.send(EventTopic.PUT_EVENT, event.objectValue());
                    }
                }
            });

            socket.handler(parser::handle);
        });

        server.listen(res -> {
            if (res.succeeded()) {
                startPromise.complete();
            } else {
                startPromise.fail(res.cause());
            }
        });
    }
}
