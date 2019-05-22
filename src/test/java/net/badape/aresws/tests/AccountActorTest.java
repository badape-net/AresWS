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
import net.badape.aresws.actors.AccountActor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@DisplayName("👋 Testing Account Management")
@ExtendWith(VertxExtension.class)
public class AccountActorTest {

    @TestMethodOrder(OrderAnnotation.class)
    @DisplayName("➡️ A nested test with customized lifecycle")
    @Nested
    class CustomLifecycleTest {

        Vertx vertx;

        @Test
        @Order(1)
        @Timeout(value = 60000, timeUnit = TimeUnit.SECONDS)
        @DisplayName("⬆️ Create 100 Accounts")
        void create1000DevAccounts(VertxTestContext testContext) {
            final int maxCount = 1000;
            Checkpoint deploymentCheckpoint = testContext.checkpoint();
            Checkpoint newAccountCheckpoint = testContext.checkpoint(maxCount);
            vertx.deployVerticle(new AccountActor(), testContext.succeeding(id -> {
                deploymentCheckpoint.flag();
                for (int i = 0; i < maxCount; i++) {
                    JsonObject message = new JsonObject().put("devId", i);
                    vertx.eventBus().<JsonObject>send(EventTopic.GET_DEV_ACCOUNT, message, reply -> {
                        testContext.verify(() -> {
                            JsonObject response = reply.result().body();
                            assertThat(response.getLong("accountId"));
                            assertThat(response.getBoolean("new", false)).isTrue();
                            assertThat(reply.succeeded()).isTrue();
                            newAccountCheckpoint.flag();

                        });
                    });
                }
            }));
        }

        @Test
        @Order(2)
        @Timeout(value = 60000, timeUnit = TimeUnit.SECONDS)
        @DisplayName("⬆️ Get 100 Accounts")
        void get1000DevAccounts(VertxTestContext testContext) {
            final int maxCount = 1000;
            Checkpoint deploymentCheckpoint = testContext.checkpoint();
            Checkpoint newAccountCheckpoint = testContext.checkpoint(maxCount);
            vertx.deployVerticle(new AccountActor(), testContext.succeeding(id -> {
                deploymentCheckpoint.flag();
                for (int i = 0; i < maxCount; i++) {
                    JsonObject message = new JsonObject().put("devId", i);
                    vertx.eventBus().<JsonObject>send(EventTopic.GET_DEV_ACCOUNT, message, reply -> {
                        testContext.verify(() -> {
                            log.info(reply.result().body().encode());
                            assertThat(reply.result().body().getBoolean("new", false)).isFalse();
                            assertThat(reply.succeeded()).isTrue();
                            newAccountCheckpoint.flag();

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
