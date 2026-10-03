package dev.varshit.proctor.role.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record CreateRoleRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @Min(1) @Max(1000) int level,
        Set<UUID> permissions,
        Boolean revealIdentity,
        Map<String, Object> customFields
) {
    public boolean revealIdentityOrDefault() {
        return Boolean.TRUE.equals(revealIdentity);
    }

    public Set<UUID> permissionsOrEmpty() {
        return permissions == null ? Set.of() : permissions;
    }
}
