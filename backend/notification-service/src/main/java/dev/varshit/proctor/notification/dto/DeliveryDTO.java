package dev.varshit.proctor.notification.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DeliveryDTO(
        UUID id,
        UUID notificationId,
        String channel,
        String status,
        String detail,
        UUID deviceId,
        OffsetDateTime createdAt
) {
}
