package dev.varshit.proctor.role.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record PatchRoleRequest(
        @Size(min = 1, max = 100) String name,
        @Size(max = 500) String description,
        @Min(1) @Max(1000) Integer level,
        Boolean revealIdentity,
        Map<String, Object> customFields
) {
}
