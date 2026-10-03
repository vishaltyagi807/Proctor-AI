package dev.varshit.proctor.common.dto;

import java.time.OffsetDateTime;

public record ApiError(
        String message,
        int statusCode,
        String status,
        OffsetDateTime timestamp
) {
    public static ApiError of(int statusCode, String status, String message) {
        return new ApiError(message, statusCode, status, OffsetDateTime.now());
    }
}
