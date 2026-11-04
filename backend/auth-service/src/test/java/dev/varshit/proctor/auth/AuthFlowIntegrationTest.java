package dev.varshit.proctor.auth;

import com.fasterxml.jackson.databind.JsonNode;
import dev.varshit.proctor.testsupport.TestDatabase;
import dev.varshit.proctor.testsupport.TestRedis;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class AuthFlowIntegrationTest {

    private static final int MAX_FAILURES = 3;
    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("R2DBC_URL", TestDatabase::r2dbcUrl);
        registry.add("AUTHENTICATOR_USER_NAME", () -> TestDatabase.AUTHENTICATOR_USER);
        registry.add("AUTHENTICATOR_USER_PASSWORD", () -> TestDatabase.AUTHENTICATOR_PASSWORD);
        registry.add("REDIS_HOST", TestRedis::host);
        registry.add("REDIS_PORT", TestRedis::port);
        registry.add("JWT_SECRET", () -> "test-secret-that-is-definitely-longer-than-32-bytes");
        registry.add("JWT_ISSUER", () -> "proctor.test");
        registry.add("APP_ALLOWED_ORIGINS", () -> "http://localhost:5173");
        registry.add("APP_COOKIE_SECURE", () -> "true");
        registry.add("LOGIN_MAX_FAILURES", () -> MAX_FAILURES);
        registry.add("eureka.client.enabled", () -> "false");
    }

    @Test
    void validCredentialsReturnTokensAndSecureCookies() {
        String email = createUser();

        var result = login(email, PASSWORD, Map.of("X-Token-Delivery", "body"))
                .expectStatus().isOk()
                .expectBody(JsonNode.class).returnResult();

        JsonNode body = result.getResponseBody();
        assertThat(body.at("/user/email").asText()).isEqualTo(email);
        assertThat(body.at("/tokenInfo/accessToken").asText()).isNotBlank();
        assertThat(body.at("/tokenInfo/refreshToken").asText()).isNotBlank();
        List<String> cookies = result.getResponseHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(2).allSatisfy(cookie -> assertThat(cookie).contains("HttpOnly", "Secure"));
    }

    @Test
    void tokensStayOutOfTheBodyUnlessRequested() {
        String email = createUser();

        JsonNode body = login(email, PASSWORD, Map.of()).expectStatus().isOk()
                .expectBody(JsonNode.class).returnResult().getResponseBody();

        assertThat(body.at("/tokenInfo/accessToken").isMissingNode()).isTrue();
        assertThat(body.at("/tokenInfo/refreshToken").isMissingNode()).isTrue();
    }

    @Test
    void wrongPasswordAndUnknownEmailLookTheSame() {
        String email = createUser();

        String wrong = login(email, "not the password", Map.of()).expectStatus().isUnauthorized()
                .expectBody(JsonNode.class).returnResult().getResponseBody().at("/message").asText();
        String unknown = login("nobody-" + UUID.randomUUID() + "@test.local", PASSWORD, Map.of())
                .expectStatus().isUnauthorized()
                .expectBody(JsonNode.class).returnResult().getResponseBody().at("/message").asText();

        assertThat(wrong).isEqualTo(unknown);
    }

    @Test
    void disabledAccountCannotSignIn() throws SQLException {
        String email = createUser();
        superuser("update users set enabled = false where email = ?", email);

        login(email, PASSWORD, Map.of()).expectStatus().isUnauthorized();
    }

    @Test
    void refreshRotatesAndReuseRevokesTheWholeFamily() {
        String email = createUser();
        String first = refreshTokenOf(login(email, PASSWORD, Map.of("X-Token-Delivery", "body")));

        String second = refreshTokenOf(refresh(first).expectStatus().isOk());
        assertThat(second).isNotEqualTo(first);

        refresh(first).expectStatus().isUnauthorized();
        refresh(second).expectStatus().isUnauthorized();
    }

    @Test
    void logoutRevokesTheRefreshToken() {
        String email = createUser();
        String token = refreshTokenOf(login(email, PASSWORD, Map.of("X-Token-Delivery", "body")));

        client.post().uri("/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refreshToken", token))
                .exchange().expectStatus().isOk();

        refresh(token).expectStatus().isUnauthorized();
    }

    @Test
    void logoutAllRequiresAValidAccessToken() {
        client.post().uri("/auth/logout-all").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                .exchange().expectStatus().isUnauthorized();

        String email = createUser();
        JsonNode body = login(email, PASSWORD, Map.of("X-Token-Delivery", "body")).expectStatus().isOk()
                .expectBody(JsonNode.class).returnResult().getResponseBody();
        String access = body.at("/tokenInfo/accessToken").asText();
        String refreshToken = body.at("/tokenInfo/refreshToken").asText();

        client.post().uri("/auth/logout-all").header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .exchange().expectStatus().isOk();
        refresh(refreshToken).expectStatus().isUnauthorized();
    }

    @Test
    void repeatedFailuresLockTheAccountEvenForTheRightPassword() {
        String email = createUser();
        for (int i = 0; i < MAX_FAILURES; i++) {
            login(email, "wrong-" + i, Map.of()).expectStatus().isUnauthorized();
        }

        login(email, PASSWORD, Map.of()).expectStatus().isEqualTo(429)
                .expectBody(JsonNode.class)
                .value(body -> assertThat(body.at("/message").asText()).contains("Too many failed sign-in attempts"));
    }

    @Test
    void lockoutIsPerAccountAndIgnoresEmailCase() {
        String locked = createUser();
        String other = createUser();
        for (int i = 0; i < MAX_FAILURES; i++) {
            login(locked.toUpperCase(), "wrong-" + i, Map.of()).expectStatus().isUnauthorized();
        }

        login(locked, PASSWORD, Map.of()).expectStatus().isEqualTo(429);
        login(other, PASSWORD, Map.of()).expectStatus().isOk();
    }

    @Test
    void successfulSignInResetsTheFailureCount() {
        String email = createUser();
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < MAX_FAILURES - 1; i++) {
                login(email, "wrong-" + i, Map.of()).expectStatus().isUnauthorized();
            }
            login(email, PASSWORD, Map.of()).expectStatus().isOk();
        }
    }

    @Test
    void sessionRecordsTheGatewayResolvedClientAddressNotTheForwardedHeader() throws SQLException {
        String email = createUser();

        login(email, PASSWORD, Map.of("X-Client-IP", "203.0.113.9", "X-Forwarded-For", "6.6.6.6"))
                .expectStatus().isOk();

        try (Connection db = TestDatabase.connectAsSuperuser();
             PreparedStatement ps = db.prepareStatement("select t.ip_address from refresh_tokens t"
                     + " join users u on u.id = t.user_id where u.email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("203.0.113.9");
            }
        }
    }

    @Test
    void seededAdministratorCanSignIn() {
        login(TestDatabase.ADMIN_EMAIL, TestDatabase.ADMIN_PASSWORD, Map.of()).expectStatus().isOk();
    }

    private WebTestClient.ResponseSpec login(String email, String password, Map<String, String> headers) {
        return client.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> headers.forEach(h::add))
                .bodyValue(Map.of("email", email, "password", password))
                .exchange();
    }

    private WebTestClient.ResponseSpec refresh(String token) {
        return client.post().uri("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Token-Delivery", "body")
                .bodyValue(Map.of("refreshToken", token))
                .exchange();
    }

    private static String refreshTokenOf(WebTestClient.ResponseSpec response) {
        JsonNode body = response.expectStatus().isOk().expectBody(JsonNode.class).returnResult().getResponseBody();
        return body.at("/tokenInfo/refreshToken").asText();
    }

    private static String createUser() {
        String email = "user-" + UUID.randomUUID() + "@test.local";
        try {
            superuser("insert into users (email, name, password, enabled, verified)"
                    + " values (?, 'Test', crypt('" + PASSWORD + "', gen_salt('bf', 4)), true, true)", email);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return email;
    }

    private static void superuser(String sql, String arg) throws SQLException {
        try (Connection db = TestDatabase.connectAsSuperuser(); PreparedStatement ps = db.prepareStatement(sql)) {
            ps.setString(1, arg);
            ps.executeUpdate();
        }
    }
}
