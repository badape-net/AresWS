package net.badape.aresws.tests;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import net.badape.aresws.AresWebServices;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@DisplayName("👋 A fairly basic test example")
@ExtendWith(VertxExtension.class)
public class AresWebServicesTest {

    @DisplayName("➡️ A nested test with customized lifecycle")
    @Nested
    class CustomLifecycleTest {

        Vertx vertx;

        @BeforeEach
        void prepare() {
            VertxOptions opts = new VertxOptions()
                    .setMaxEventLoopExecuteTime(10000)
                    .setPreferNativeTransport(true);
            opts.getFileSystemOptions().setFileCachingEnabled(true);

            vertx = Vertx.vertx(opts);

        }

        @Test
        @DisplayName("⬆️ Deploy HeroStoreActor")
        void deploySampleVerticle(VertxTestContext testContext) {
            vertx.deployVerticle(new AresWebServices(), testContext.succeeding(id -> testContext.completeNow()));
        }

        @Test
        @Timeout(value = 60000, timeUnit = TimeUnit.SECONDS)
        @DisplayName("🛂 Make a HTTP client request to SampleVerticle")
        void httpRequest(VertxTestContext testContext) {
            WebClient webClient = WebClient.create(vertx);
            int maxCount = 100;
            Checkpoint deploymentCheckpoint = testContext.checkpoint();
            Checkpoint requestCheckpoint = testContext.checkpoint(maxCount);

            vertx.deployVerticle(new AresWebServices(), testContext.succeeding(id -> {
                deploymentCheckpoint.flag();

                vertx.setPeriodic(1, timer -> {
                    final long generatedLong = new Random().nextLong() & 0xffffffffL;

                    webClient.get(8765, "localhost", "/player/dev/" + generatedLong)
                            .as(BodyCodec.string())
                            .send(testContext.succeeding(resp -> {
                                testContext.verify(() -> {
                                    assertThat(resp.statusCode()).isEqualTo(200);
                                    assertThat(resp.body()).contains("playerId");
                                    requestCheckpoint.flag();
                                });
                            }));
                });
            }));
        }

        @AfterEach
        void cleanup() {
            vertx.close();
        }
    }
}
