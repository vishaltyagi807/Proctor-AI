package dev.varshit.proctor.auth.service;

import dev.varshit.proctor.auth.dto.ClientContext;
import dev.varshit.proctor.auth.dto.IssuedRefreshToken;
import dev.varshit.proctor.auth.repository.RefreshTokenRepository;
import dev.varshit.proctor.common.exception.UnauthorizedException;
import dev.varshit.proctor.security.jwt.JwtProperties;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class DatabaseRefreshTokenService implements RefreshTokenService {

    private static final String INVALID = "Refresh token is invalid or expired";
    private static final int TOKEN_BYTES = 48;

    private final RefreshTokenRepository repository;
    private final JwtProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public DatabaseRefreshTokenService(RefreshTokenRepository repository, JwtProperties properties, Clock clock) {
        if (properties.refreshTokenExpiresIn() <= 0) {
            throw new IllegalStateException("JWT_REFRESH_EXPIRE_IN_SEC must be greater than zero");
        }
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Mono<IssuedRefreshToken> start(UUID userId, ClientContext client) {
        return issue(userId, UUID.randomUUID(), client);
    }

    @Override
    public Mono<Rotation> rotate(String rawToken, ClientContext client) {
        String hash = hash(rawToken);
        return repository.claim(hash).flatMap(claimed -> repository.findByHash(hash).flatMap(record -> {
            if (claimed) {
                return Mono.just(new Rotation(record.userId(), record.familyId()));
            }
            Mono<Long> reaction = record.revokedAt() != null ? repository.revokeFamily(record.familyId()) : Mono.just(0L);
            return reaction.then(Mono.<Rotation>error(new UnauthorizedException(INVALID)));
        })).switchIfEmpty(Mono.error(new UnauthorizedException(INVALID)));
    }

    @Override
    public Mono<Void> revoke(String rawToken) {
        return repository.revokeByHash(hash(rawToken)).then();
    }

    @Override
    public Mono<Void> revokeAll(UUID userId) {
        return repository.revokeAllForUser(userId).then();
    }

    public Mono<IssuedRefreshToken> issue(UUID userId, UUID familyId, ClientContext client) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        OffsetDateTime expiresAt = OffsetDateTime.ofInstant(
                clock.instant().plusSeconds(properties.refreshTokenExpiresIn()), ZoneOffset.UTC);
        return repository.insert(userId, familyId, hash(raw), expiresAt, client)
                .thenReturn(new IssuedRefreshToken(raw, expiresAt, properties.refreshTokenExpiresIn(), familyId));
    }

    private String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
