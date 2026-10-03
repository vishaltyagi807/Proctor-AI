package dev.varshit.proctor.common.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record DepartmentDTO(
        UUID id,
        String name,
        String code,
        String description,
        boolean active,
        Map<String, Object> customFields,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
