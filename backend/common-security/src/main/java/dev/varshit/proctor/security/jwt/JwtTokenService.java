package dev.varshit.proctor.security.jwt;

import dev.varshit.proctor.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

public class JwtTokenService implements TokenService {

    private static final String ID_CLAIM = "id";
    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties properties;
    private final SecretKey key;
    private final Clock clock;

    public JwtTokenService(JwtProperties properties, Clock clock) {
        byte[] secret = properties.secret() == null ? new byte[0] : properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT secret must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(secret);
        this.clock = clock;
    }

    @Override
    public IssuedToken issue(UserPrincipal principal) {
        Instant now = clock.instant();
        Instant expiry = now.plusSeconds(properties.accessTokenExpiresIn());
        String token = Jwts.builder()
                .subject(principal.email())
                .claim(ID_CLAIM, principal.id().toString())
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
        return new IssuedToken(
                token,
                properties.accessTokenExpiresIn(),
                OffsetDateTime.ofInstant(now, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(expiry, ZoneOffset.UTC)
        );
    }

    @Override
    public Optional<UserPrincipal> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.issuer())
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new UserPrincipal(UUID.fromString(claims.get(ID_CLAIM, String.class)), claims.getSubject()));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
