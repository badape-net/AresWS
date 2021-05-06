package net.badape.aresws;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import net.badape.aresws.services.AresAccount;
import net.badape.aresws.services.AresStore;
import net.badape.aresws.services.ElasticSearchClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@DisplayName("👋 A set of tests for the elastic search client")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(VertxExtension.class)
public class ElasticSearchTest {

    @BeforeAll
    static void deploy_verticle(Vertx vertx, VertxTestContext testContext) {
        log.info("deploying ares account");
        vertx.deployVerticle(ElasticSearchClient.class.getName(), testContext.succeedingThenComplete());
    }

    @AfterEach
    @DisplayName("Check that the verticle is still there")
    void lastChecks(Vertx vertx) {
        assertThat(vertx.deploymentIDs())
                .isNotEmpty()
                .hasSize(1);
    }

    @Order(1)
    @DisplayName("Create Basic Events")
    @ParameterizedTest(name = "{index} => eventType={0}")
    @CsvFileSource(resources = "/events.csv")
    void putEvents(String eventType, Vertx vertx, VertxTestContext testContext) throws InterruptedException {

        EventBus eb = vertx.eventBus();

        JsonObject message = new JsonObject().put("eventName", eventType);
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        message.put("timestamp", timestamp.toString());

        eb.<JsonObject>request(EventTopic.PUT_EVENT, message, result -> {
            if (result.succeeded()) {
                testContext.completeNow();
            } else {
             testContext.failNow(result.cause().getMessage());
            }

        });

    }

    static private Future<String> deployHelper(Vertx vertx, String name) {
        Promise<String> promise = Promise.promise();
        DeploymentOptions options = new DeploymentOptions();
        vertx.deployVerticle(name, options, promise);
        return promise.future();
    }

}
