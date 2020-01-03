package net.badape.aresws.tests;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import net.badape.aresws.EventTopic;
import net.badape.aresws.services.AresStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@DisplayName("👋 Testing the Hero Store Actor")
@ExtendWith(VertxExtension.class)
public class StoreActorTest {

    @DisplayName("➡️ A nested test with customized lifecycle")
    @TestMethodOrder(OrderAnnotation.class)
    @Nested
    class CustomLifecycleTest {

        Vertx vertx;

        @Test
        @Timeout(value = 60000, timeUnit = TimeUnit.SECONDS)
        @DisplayName("⬆️ Create 100 Accounts")
        @Order(1)
        void create100Accounts(VertxTestContext testContext) {
            final int maxCount = 100;
            Checkpoint deploymentCheckpoint = testContext.checkpoint();
            Checkpoint newAccountCheckpoint = testContext.checkpoint(maxCount);
            Checkpoint buyHeroCheckpoint = testContext.checkpoint(maxCount);
            vertx.deployVerticle(new AresStore(), testContext.succeeding(id -> {
                deploymentCheckpoint.flag();

                for (int i = 0; i < maxCount; i++) {

                    final long generatedLong = new Random().nextLong() & 0xffffffffL;
                    JsonObject message = new JsonObject().put("accountId", generatedLong);

                    vertx.eventBus().<JsonObject>send(EventTopic.NEW_ACCOUNT, message, reply -> {
                        testContext.verify(() -> {
                            log.info(reply.result().body().encode());
                            assertThat(reply.succeeded()).isTrue();
                            newAccountCheckpoint.flag();

                            for (int heroId = 1; heroId < 9; heroId++) {
                                message.put("heroId",heroId);
                                vertx.eventBus().<JsonObject>send(EventTopic.BUY_HERO, message, hReply -> {
                                    log.info(hReply.result().body().encode());
                                    assertThat(hReply.succeeded()).isTrue();
                                    buyHeroCheckpoint.flag();
                                });
                            }

                        });
                    });
                }
            }));
        }


        @BeforeEach
        void prepare() {
            VertxOptions opts = new VertxOptions()
                    .setMaxEventLoopExecuteTime(10000)
                    .setPreferNativeTransport(true);
            opts.getFileSystemOptions().setFileCachingEnabled(true);

            vertx = Vertx.vertx(opts);

        }

        @AfterEach
        void cleanup() {
            vertx.close();
        }
    }
}
