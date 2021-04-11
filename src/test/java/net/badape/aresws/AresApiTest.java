package net.badape.aresws;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import net.badape.aresws.services.APIVerticle;
import net.badape.aresws.services.AresAccount;
import net.badape.aresws.services.AresStore;
import net.badape.aresws.services.HeroContentRefresh;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@DisplayName("👋 A set of tests for the Ares WS API verticle")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(VertxExtension.class)
public class AresApiTest {
    @BeforeAll
    static void deploy_verticle(Vertx vertx, VertxTestContext testContext) {
        log.info("deploying ares account");
        vertx.deployVerticle(AresAccount.class.getName(), testContext.succeedingThenComplete());
        final List<Future> sFutures = List.of(
                deployHelper(vertx, HeroContentRefresh.class.getName()),
                deployHelper(vertx, AresStore.class.getName()),
                deployHelper(vertx, AresAccount.class.getName()),
                deployHelper(vertx, APIVerticle.class.getName())
        );
    }

    @AfterEach
    @DisplayName("Check that the verticle is still there")
    void lastChecks(Vertx vertx) {
        assertThat(vertx.deploymentIDs())
                .isNotEmpty()
                .hasSize(4);
    }

    @Order(1)
    @Test
    @DisplayName("Refresh Hero Store")
    void refreshHeroStore(Vertx vertx, VertxTestContext testContext) throws InterruptedException {
        vertx.eventBus().send(EventTopic.FORCE_HERO_REFRESH, "refresh");
        Thread.sleep(10000);
        testContext.completeNow();
    }

    @Order(2)
    @DisplayName("⏱ call Health API")
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    @Test
    void testHealth(Vertx vertx, VertxTestContext testContext) {
        WebClient client = newWebClient(vertx);

        client.get(8765, "localhost", "/health").send().onSuccess(response -> {
            log.info("Received response with status code" + response.statusCode());

            if (response.statusCode() != 200) {
                testContext.failNow("response code: " + response.statusCode());
            } else {
                testContext.completeNow();
            }

        }).onFailure(err -> {
            log.error("Something went wrong " + err.getMessage());
            testContext.failNow(err);
        });

    }

    @Order(3)
    @DisplayName("⏱ get EOS account roster")
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    @ParameterizedTest(name = "{index} => accountid={0}")
    @CsvFileSource(resources = "/accounts01.csv")
    void getAccountRoster(String accountId, Vertx vertx, VertxTestContext testContext) {

        WebClient client = newWebClient(vertx);
        String path = "/store/" + accountId + "/roster";

        client.get(8765, "localhost", path).send().onSuccess(response -> {
            log.info("Received response with status code" + response.statusCode());

            if (response.statusCode() != 200) {
                testContext.failNow("response code: " + response.statusCode());
            } else {
                log.info(response.body().toJsonObject().encode());
                JsonArray heroRoster = response.body().toJsonObject().getJsonArray("data");
                assertThat(heroRoster.size()).isNotZero();
                testContext.completeNow();
            }

        }).onFailure(err -> {
            log.error("Something went wrong " + err.getMessage());
            testContext.failNow(err);
        });
    }

    @Order(4)
    @DisplayName("⏱ get EOS account roster")
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    @ParameterizedTest(name = "{index} => accountid={0}")
    @CsvFileSource(resources = "/accounts01.csv")
    void getAccountTeam(String accountId, Vertx vertx, VertxTestContext testContext) {

        WebClient client = newWebClient(vertx);
        String path = "/store/" + accountId + "/team";

        client.get(8765, "localhost", path).send().onSuccess(response -> {
            log.info("Received response with status code" + response.statusCode());

            if (response.statusCode() != 200) {
                testContext.failNow("response code: " + response.statusCode());
            } else {
                log.info(response.body().toJsonObject().encode());
                JsonArray heroRoster = response.body().toJsonObject().getJsonArray("data");
                assertThat(heroRoster.size()).isZero();
                testContext.completeNow();
            }

        }).onFailure(err -> {
            log.error("Something went wrong " + err.getMessage());
            testContext.failNow(err);
        });
    }

    @Order(4)
    @DisplayName("⏱ buy all heroes roster")
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    @ParameterizedTest(name = "{index} => accountid={0}")
    @CsvFileSource(resources = "/accounts01.csv")
    void buyAccountHeroes(String accountId, Vertx vertx, VertxTestContext testContext) {

        WebClient client = newWebClient(vertx);
        String pathRoster = "/store/" + accountId + "/roster";

        client.get(8765, "localhost", pathRoster).send().onSuccess(response -> {
            log.info("Received response with status code" + response.statusCode());

            if (response.statusCode() != 200) {
                testContext.failNow("response code: " + response.statusCode());
            } else {
                log.info(response.body().toJsonObject().encode());
                JsonArray heroRoster = response.body().toJsonObject().getJsonArray("data");
                assertThat(heroRoster.size()).isNotZero();

                heroRoster.forEach(heroObject ->{
                    JsonObject heroData = (JsonObject) heroObject;
                    Long heroPk = heroData.getLong("hero_pk");
                    JsonObject buyMessage = new JsonObject().put("hero_id", heroPk);

                    client.post(8765, "localhost", pathRoster).sendJsonObject(buyMessage).onSuccess(buyResponse -> {
                        log.info("Received response with status code: " + buyResponse.statusCode());

                        testContext.verify(() -> {
                            assertThat(buyResponse.statusCode()).isEqualTo(200);
                        });
                        if (buyResponse.statusCode() != 200) {
                            testContext.failNow("response code: " + buyResponse.statusCode());
                        } else {
                            testContext.completeNow();
                        }
                    });
                });
            }

        }).onFailure(err -> {
            log.error("Something went wrong " + err.getMessage());
            testContext.failNow(err);
        });
    }

    private WebClient newWebClient(Vertx vertx) {
        WebClientOptions options = new WebClientOptions()
                .setUserAgent("My-App/1.2.3")
                .setKeepAlive(true);
        return WebClient.create(vertx, options);
    }

    static private Future<String> deployHelper(Vertx vertx, String name) {
        Promise<String> promise = Promise.promise();
        DeploymentOptions options = new DeploymentOptions();
        vertx.deployVerticle(name, options, promise);
        return promise.future();
    }
}
