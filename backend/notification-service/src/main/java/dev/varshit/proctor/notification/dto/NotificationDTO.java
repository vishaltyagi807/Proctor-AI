package dev.varshit.proctor.notification.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record NotificationDTO(
        UUID id,
        UUID userId,
        String type,
        String title,
        String body,
        Map<String, Object> data,
        String priority,
        String source,
        OffsetDateTime readAt,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt
) {
}
