package net.badape.aresws.services;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AresAnalyticsReciever extends AbstractVerticle {

    public void start(Promise<Void> startPromise) {
        NetServerOptions options = new NetServerOptions().setPort(9876);
        NetServer server = vertx.createNetServer(options);

        server.connectHandler(socket -> {
            socket.handler(buffer -> {
                log.info(buffer.toJsonObject().encode());
            });
        });

        server.listen(9876, "localhost", res -> {
            if (res.succeeded()) {
                startPromise.complete();
            } else {
                startPromise.fail(res.cause());
            }
        });
    }
}
