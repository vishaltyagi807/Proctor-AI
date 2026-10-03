package dev.varshit.proctor.common.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record RoleDTO(
        UUID id,
        String name,
        String description,
        int level,
        boolean system,
        boolean superuser,
        boolean revealIdentity,
        Map<String, Object> customFields,
        OffsetDateTime createdAt
) {
}
