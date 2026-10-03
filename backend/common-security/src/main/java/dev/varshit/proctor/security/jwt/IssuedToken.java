package dev.varshit.proctor.security.jwt;

import java.time.OffsetDateTime;

public record IssuedToken(String value, long expiresIn, OffsetDateTime issuedAt, OffsetDateTime expiresAt) {
}
