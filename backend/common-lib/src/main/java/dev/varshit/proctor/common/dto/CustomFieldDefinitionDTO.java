package dev.varshit.proctor.common.dto;

import dev.varshit.proctor.common.enums.CustomFieldEntity;
import dev.varshit.proctor.common.enums.CustomFieldType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CustomFieldDefinitionDTO(
        UUID id,
        CustomFieldEntity entity,
        String key,
        String label,
        String helpText,
        CustomFieldType dataType,
        boolean required,
        List<String> options,
        Object defaultValue,
        BigDecimal minValue,
        BigDecimal maxValue,
        Integer maxLength,
        String pattern,
        UUID appliesToRoleId,
        UUID appliesToDepartmentId,
        int sortOrder,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
