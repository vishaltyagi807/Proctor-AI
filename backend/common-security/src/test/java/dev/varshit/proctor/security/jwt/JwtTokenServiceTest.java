package dev.varshit.proctor.security.jwt;

import dev.varshit.proctor.security.UserPrincipal;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final String SECRET = "a-test-secret-that-is-longer-than-thirty-two-bytes";
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final UserPrincipal ALICE = new UserPrincipal(UUID.randomUUID(), "alice@test.local");

    @Test
    void issuedTokenVerifiesToTheSamePrincipal() {
        JwtTokenService service = service(SECRET, "proctor", Clock.fixed(NOW, ZoneOffset.UTC));

        IssuedToken token = service.issue(ALICE);

        assertThat(token.expiresIn()).isEqualTo(900);
        assertThat(service.verify(token.value())).contains(ALICE);
    }

    @Test
    void expiredTokenIsRejected() {
        String token = service(SECRET, "proctor", Clock.fixed(NOW, ZoneOffset.UTC)).issue(ALICE).value();

        JwtTokenService later = service(SECRET, "proctor", Clock.fixed(NOW.plusSeconds(901), ZoneOffset.UTC));

        assertThat(later.verify(token)).isEmpty();
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        String token = service(SECRET, "someone-else", Clock.fixed(NOW, ZoneOffset.UTC)).issue(ALICE).value();

        assertThat(service(SECRET, "proctor", Clock.fixed(NOW, ZoneOffset.UTC)).verify(token)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = service(SECRET + "-other", "proctor", Clock.fixed(NOW, ZoneOffset.UTC)).issue(ALICE).value();

        assertThat(service(SECRET, "proctor", Clock.fixed(NOW, ZoneOffset.UTC)).verify(token)).isEmpty();
    }

    @Test
    void tamperedPayloadIsRejected() {
        JwtTokenService service = service(SECRET, "proctor", Clock.fixed(NOW, ZoneOffset.UTC));
        String[] parts = service.issue(ALICE).value().split("\\.");
        String forged = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "AA." + parts[2];

        assertThat(service.verify(forged)).isEmpty();
    }

    @Test
    void unsignedTokenIsRejected() {
        JwtTokenService service = service(SECRET, "proctor", Clock.fixed(NOW, ZoneOffset.UTC));
        String[] parts = service.issue(ALICE).value().split("\\.");
        String header = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(service.verify(header + "." + parts[1] + ".")).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        JwtTokenService service = service(SECRET, "proctor", Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.verify("not-a-token")).isEmpty();
        assertThat(service.verify("")).isEmpty();
    }

    @Test
    void shortSecretRefusesToStart() {
        assertThatThrownBy(() -> service("too-short", "proctor", Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service(null, "proctor", Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }

    private static JwtTokenService service(String secret, String issuer, Clock clock) {
        return new JwtTokenService(new JwtProperties(secret, issuer, 900, 2_592_000), clock);
    }
}
