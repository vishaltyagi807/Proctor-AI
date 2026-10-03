package dev.varshit.proctor.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CreateDepartmentRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 50) String code,
        @Size(max = 2000) String description,
        Boolean active,
        Map<String, Object> customFields
) {
    public boolean activeOrDefault() {
        return active == null || active;
    }
}
