package dev.varshit.proctor.gateway;

import dev.varshit.proctor.testsupport.TestRedis;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginRateLimitIntegrationTest {

    @LocalServerPort
    private int port;

    private WebTestClient client;

    @BeforeEach
    void connect() {
        client = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("REDIS_HOST", TestRedis::host);
        registry.add("REDIS_PORT", TestRedis::port);
        registry.add("APP_ALLOWED_ORIGINS", () -> "http://localhost:5173");
        registry.add("APP_TRUSTED_PROXIES", () -> 1);
        registry.add("LOGIN_RATE_LIMIT_REPLENISH", () -> 1);
        registry.add("LOGIN_RATE_LIMIT_BURST", () -> 6);
        registry.add("LOGIN_RATE_LIMIT_COST", () -> 3);
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("app.monitoring.enabled", () -> "false");
    }

    @Test
    void loginIsLimitedPerClientAddress() {
        String attacker = randomAddress();
        String bystander = randomAddress();

        List<HttpStatusCode> statuses = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            statuses.add(login(attacker));
        }

        assertThat(statuses.subList(0, 2)).doesNotContain(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(statuses.get(2)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(login(bystander)).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void spoofingTheForwardedHeaderDoesNotResetTheLimit() {
        String client = randomAddress();
        for (int i = 0; i < 2; i++) {
            loginVia(randomAddress() + ", " + client);
        }

        assertThat(loginVia(randomAddress() + ", " + client)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void otherRoutesAreNotLimited() {
        String address = randomAddress();
        for (int i = 0; i < 10; i++) {
            HttpStatusCode status = client.get().uri("/users/me").header("X-Forwarded-For", address)
                    .exchange().returnResult(Void.class).getStatus();
            assertThat(status).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    private HttpStatusCode login(String address) {
        return loginVia(address);
    }

    private HttpStatusCode loginVia(String forwardedFor) {
        return client.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", forwardedFor)
                .bodyValue("{\"email\":\"a@b.c\",\"password\":\"x\"}")
                .exchange().returnResult(Void.class).getStatus();
    }

    private static String randomAddress() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return "198.51." + random.nextInt(256) + "." + random.nextInt(1, 255);
    }
}
