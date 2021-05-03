package net.badape.aresws;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import net.badape.aresws.services.AresAccount;
import net.badape.aresws.services.AresStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@DisplayName("👋 A set of tests for the account verticle")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(VertxExtension.class)
public class AccountTest {

    @BeforeAll
    static void deploy_verticle(Vertx vertx, VertxTestContext testContext) {
        log.info("deploying ares account");
        vertx.deployVerticle(AresAccount.class.getName(), testContext.succeedingThenComplete());
        final List<Future> sFutures = List.of(
                deployHelper(vertx, AresStore.class.getName()),
                deployHelper(vertx, AresAccount.class.getName())
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

        Thread.sleep(10000);
        testContext.completeNow();
    }

    @Order(2)
    @DisplayName("⏱ create EOS account01")
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    @ParameterizedTest(name = "{index} => accountid={0}")
    @CsvFileSource(resources = "/accounts01.csv")
    void createAccounts(String accountId, Vertx vertx, VertxTestContext testContext) {

        JsonObject aresAccount = new JsonObject().put("account_id", accountId);

        vertx.eventBus().<JsonObject>request(EventTopic.GET_EOS_ACCOUNT, aresAccount, reply -> {
            assertThat(reply.succeeded()).isTrue();
            if (reply.succeeded()) {
                testContext.completeNow();
            } else {
                log.error(reply.cause().getMessage());
                testContext.failNow(reply.cause());
            }
        });
    }

    @Order(3)
    @DisplayName("⏱ get EOS account")
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    @ParameterizedTest(name = "{index} => accountid={0}")
    @CsvFileSource(resources = "/accounts01.csv")
    void getCreatedAccount(String accountId, Vertx vertx, VertxTestContext testContext) {
        JsonObject aresAccount = new JsonObject().put("account_id", accountId);

        vertx.eventBus().<JsonObject>request(EventTopic.GET_EOS_ACCOUNT, aresAccount, reply -> {
            assertThat(reply.succeeded()).isTrue();
            if (reply.succeeded()) {
                testContext.completeNow();
            } else {
                log.error(reply.cause().getMessage());
                testContext.failNow(reply.cause());
            }
        });
    }

    @Order(4)
    @DisplayName("⏱ get roster")
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    @ParameterizedTest(name = "{index} => accountid={0}")
    @CsvFileSource(resources = "/accounts01.csv")
    void getAccountRoster(String accountId, Vertx vertx, VertxTestContext testContext) {
        JsonObject eosAccount = new JsonObject().put("account_id", accountId);

        vertx.eventBus().<JsonObject>request(EventTopic.GET_EOS_ACCOUNT, eosAccount, reply -> {
            assertThat(reply.succeeded()).isTrue();
            if (reply.succeeded()) {
                log.info(reply.result().body().encode());
                JsonObject aresAccount = reply.result().body();
                vertx.eventBus().<JsonObject>request(EventTopic.GET_ROSTER, aresAccount, rReply -> {
                    if (rReply.succeeded()) {
                        log.info(rReply.result().body().encode());
                        assertThat(rReply.result().body().getJsonArray("data").size()).isEqualTo(9);
                        testContext.completeNow();
                    } else {
                        testContext.failNow(rReply.cause());
                    }
                });
            } else {
                log.error(reply.cause().getMessage());
                testContext.failNow(reply.cause());
            }
        });
    }

    @Order(5)
    @DisplayName("⏱ buy all the heroes")
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    @ParameterizedTest(name = "{index} => accountid={0}")
    @CsvFileSource(resources = "/accounts01.csv")
    void buyAllHeroes(String accountId, Vertx vertx, VertxTestContext testContext) {
        log.info("buyAllHeroes:" + accountId);
        JsonObject eosAccount = new JsonObject().put("account_id", accountId);

        vertx.eventBus().<JsonObject>request(EventTopic.GET_EOS_ACCOUNT, eosAccount, reply -> {
            assertThat(reply.succeeded()).isTrue();
            if (reply.succeeded()) {
                log.info("got ares account:" + reply.result().body().encode());
                JsonObject aresAccount = reply.result().body();
                Long aresAccountId = aresAccount.getLong("accountId");
                vertx.eventBus().<JsonObject>request(EventTopic.GET_ROSTER, aresAccount, rReply -> {
                    if (rReply.succeeded()) {

                        assertThat(rReply.result().body().getJsonArray("data").size()).isEqualTo(9);
                        JsonArray heroData = rReply.result().body().getJsonArray("data");
                        heroData.forEach(heroObject -> {
                            JsonObject hero = (JsonObject) heroObject;
                            Long heroId = hero.getLong("hero_pk");
                            Long balance = hero.getLong("balance");
                            Long credits = hero.getLong("credits");

                            JsonObject message = new JsonObject()
                                    .put("accountId", aresAccountId)
                                    .put("heroId", heroId);

                            vertx.eventBus().<JsonObject>request(EventTopic.BUY_HERO, message, bReply -> {
                                if (bReply.succeeded()) {
                                    log.info(bReply.result().body().encode());
                                    JsonArray rosterData = bReply.result().body().getJsonArray("data");
                                    log.info(rosterData.encode());
                                    assertThat(rosterData.size()).isEqualTo(9);
                                    testContext.completeNow();
                                } else {
                                    testContext.failNow(bReply.cause());
                                }
                            });

                        });

                    } else {
                        testContext.failNow(rReply.cause());
                    }
                });
            } else {
                log.error(reply.cause().getMessage());
                testContext.failNow(reply.cause());
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
