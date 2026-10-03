package dev.varshit.proctor.department.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;

public record PatchDepartmentRequest(
        @Size(min = 1, max = 200) String name,
        @Size(min = 1, max = 50) String code,
        @Size(max = 2000) String description,
        Boolean active,
        Map<String, Object> customFields
) {
}
