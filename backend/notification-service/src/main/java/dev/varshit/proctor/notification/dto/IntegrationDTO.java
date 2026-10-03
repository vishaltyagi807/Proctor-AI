package dev.varshit.proctor.notification.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record IntegrationDTO(
        String provider,
        boolean enabled,
        boolean configured,
        Map<String, Object> config,
        OffsetDateTime updatedAt
) {
}
