package dev.varshit.proctor.customfield.dto;

import dev.varshit.proctor.common.enums.CustomFieldEntity;
import dev.varshit.proctor.common.enums.CustomFieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateCustomFieldRequest(
        @NotNull CustomFieldEntity entity,
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{0,49}$", message = "must be lower snake_case, up to 50 characters") String key,
        @NotBlank @Size(max = 200) String label,
        @Size(max = 500) String helpText,
        @NotNull CustomFieldType dataType,
        Boolean required,
        @Size(max = 100) List<@NotBlank @Size(max = 200) String> options,
        Object defaultValue,
        BigDecimal minValue,
        BigDecimal maxValue,
        Integer maxLength,
        @Size(max = 500) String pattern,
        UUID appliesToRoleId,
        UUID appliesToDepartmentId,
        Integer sortOrder,
        Boolean active
) {
}
