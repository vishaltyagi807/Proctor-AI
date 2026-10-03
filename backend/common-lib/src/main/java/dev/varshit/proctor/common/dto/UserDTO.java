package dev.varshit.proctor.common.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record UserDTO(
        UUID id,
        String email,
        String name,
        boolean enabled,
        boolean verified,
        Map<String, Object> customFields,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
