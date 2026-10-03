package dev.varshit.proctor.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccessTokenDTO(
        long expiresIn,
        OffsetDateTime issuedAt,
        OffsetDateTime expiresAt,
        Long refreshExpiresIn,
        OffsetDateTime refreshExpiresAt,
        String accessToken,
        String refreshToken
) {
}
