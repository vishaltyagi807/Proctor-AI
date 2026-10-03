package dev.varshit.proctor.auth.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IssuedRefreshToken(String value, OffsetDateTime expiresAt, long expiresIn, UUID familyId) {
}
