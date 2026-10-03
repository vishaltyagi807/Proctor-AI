package dev.varshit.proctor.storageservice.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record UploadUrlResponse(
        UUID fileId,
        String uploadUrl,
        String method,
        Map<String, String> headers,
        OffsetDateTime expiresAt
) {
}
