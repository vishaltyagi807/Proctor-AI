package dev.varshit.proctor.auth.repository;

import dev.varshit.proctor.auth.dto.ClientContext;
import dev.varshit.proctor.auth.dto.RefreshTokenRecord;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface RefreshTokenRepository {

    Mono<Long> insert(UUID userId, UUID familyId, String tokenHash, OffsetDateTime expiresAt, ClientContext client);

    Mono<Boolean> claim(String tokenHash);

    Mono<RefreshTokenRecord> findByHash(String tokenHash);

    Mono<Long> revokeByHash(String tokenHash);

    Mono<Long> revokeFamily(UUID familyId);

    Mono<Long> revokeAllForUser(UUID userId);

    Mono<Long> purgeExpired(int retentionDays);
}
