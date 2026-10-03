package dev.varshit.proctor.notification.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DeviceDTO(
        UUID id,
        String platform,
        String deviceName,
        boolean active,
        OffsetDateTime lastSeenAt,
        OffsetDateTime createdAt
) {
}
