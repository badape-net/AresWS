package net.badape.aresws.tests;

import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.TimeUnit;

@Slf4j
@DisplayName("👋 Testing Account Management")
@ExtendWith(VertxExtension.class)
public class ContentfulTest {

    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("➡️ A nested test with customized lifecycle")
    @Nested
    class CustomLifecycleTest {


        @Test
        @Order(1)
        @Timeout(value = 60000, timeUnit = TimeUnit.SECONDS)
        @DisplayName("⬆️ Create 100 Accounts")


        void create1000DevAccounts(VertxTestContext testContext) {
            
        }

    }

}
