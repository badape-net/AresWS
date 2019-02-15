package net.badape.aresws.actors;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GameActor extends AbstractVerticle {

    @Override
    public void start(Future<Void> future) {
        log.info("deploying GameActor");
        future.complete();
    }

    @Override
    public void stop(Future<Void> future) {
        log.info("undeploying GameActor");
        future.complete();
    }
}
