package net.badape.aresws.services;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import lombok.extern.slf4j.Slf4j;
import net.badape.aresws.actors.GameActor;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class TCPService extends AbstractVerticle {

    Map<String, String> gameMap = new HashMap<>();

    @Override
    public void start(Future<Void> startFuture) {

        NetServerOptions netServerOptions = new NetServerOptions().setPort(4321);
        NetServer server = vertx.createNetServer(netServerOptions);

        server.connectHandler(socket -> {
            DeploymentOptions gameOptions = new DeploymentOptions().setWorker(true);
            vertx.deployVerticle(new GameActor(), gameOptions, result -> {
                gameMap.put(socket.writeHandlerID(), result.result());
            });

            // Handle the connection in here
            socket.handler(buffer -> {
//                log.info("I received some bytes: " + buffer.length());
//                log.info(buffer.toJsonObject().encodePrettily());
            });

            socket.closeHandler(v -> {
                vertx.undeploy(gameMap.get(socket.writeHandlerID()));
            });
        });

        server.listen();

        log.info("Echo server is now listening");
        startFuture.complete();

    }
}
