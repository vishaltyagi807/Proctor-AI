package dev.varshit.proctor.auth.service;

import dev.varshit.proctor.auth.dto.ClientContext;
import dev.varshit.proctor.auth.dto.IssuedRefreshToken;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface RefreshTokenService {

    Mono<IssuedRefreshToken> start(UUID userId, ClientContext client);

    Mono<Rotation> rotate(String rawToken, ClientContext client);

    Mono<Void> revoke(String rawToken);

    Mono<Void> revokeAll(UUID userId);

    record Rotation(UUID userId, UUID familyId) {
    }
}
