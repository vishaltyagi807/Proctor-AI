package dev.varshit.proctor.auth.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RefreshTokenRecord(UUID userId, UUID familyId, OffsetDateTime expiresAt, OffsetDateTime revokedAt) {
}
