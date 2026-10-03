package dev.varshit.proctor.customfield.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UpdateCustomFieldRequest(
        @NotBlank @Size(max = 200) String label,
        @Size(max = 500) String helpText,
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
