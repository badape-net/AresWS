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
import net.badape.aresws.services.AresAccount;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.util.UUID;
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
            vertx.deployVerticle(new AresAccount(), testContext.succeeding(id -> {
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
        @DisplayName("⬆️ Update 100 Accounts")
        void update100DevAccounts(VertxTestContext testContext) {
            final int maxCount = 100;
            Checkpoint deploymentCheckpoint = testContext.checkpoint();
            Checkpoint newAccountCheckpoint = testContext.checkpoint(maxCount);
            vertx.deployVerticle(new AresAccount(), testContext.succeeding(id -> {
                deploymentCheckpoint.flag();
                for (int i = 0; i < maxCount; i++) {
                    JsonObject message = new JsonObject().put("devId", i);
                    vertx.eventBus().<JsonObject>send(EventTopic.GET_DEV_ACCOUNT, message, reply -> {
                        testContext.verify(() -> {

                            assertThat(reply.result().body().getBoolean("new", false)).isFalse();
                            assertThat(reply.succeeded()).isTrue();
                            newAccountCheckpoint.flag();

                        });
                    });
                }
            }));
        }

        @Test
        @Order(3)
        @Timeout(value = 60000, timeUnit = TimeUnit.SECONDS)
        @DisplayName("⬆️ Get 100 Device Accounts")
        void get1000DeviceAccounts(VertxTestContext testContext) {
            final int maxCount = 100;
            Checkpoint deploymentCheckpoint = testContext.checkpoint();
            Checkpoint newAccountCheckpoint = testContext.checkpoint(maxCount);
            vertx.deployVerticle(new AresAccount(), testContext.succeeding(id -> {
                deploymentCheckpoint.flag();
                for (int i = 0; i < maxCount; i++) {
                    String deviceID = UUID.randomUUID().toString();
                    JsonObject message = new JsonObject().put("deviceId", deviceID);
                    vertx.eventBus().<JsonObject>send(EventTopic.GET_DEVICE_ACCOUNT, message, reply -> {
                        testContext.verify(() -> {
                            assertThat(reply.succeeded()).isTrue();

                            JsonObject response = reply.result().body();
                            if (!response.getBoolean("new", false)) {
                                log.info(deviceID + " not new : "+ response.encode());
                            } else {
                                log.info(deviceID + " new : "+ response.encode());
                            }

                            assertThat(response.getBoolean("new", false)).isTrue();

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
