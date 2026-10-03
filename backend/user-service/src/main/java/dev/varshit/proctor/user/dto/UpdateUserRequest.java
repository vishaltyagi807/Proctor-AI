package dev.varshit.proctor.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record UpdateUserRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Email @Size(max = 320) String email,
        @Size(min = 8, max = 200) String password,
        Boolean enabled,
        Boolean verified,
        Set<UUID> departments,
        Set<UUID> roles,
        Map<String, Object> customFields
) {
    public Set<UUID> departmentsOrEmpty() {
        return departments == null ? Set.of() : departments;
    }

    public Set<UUID> rolesOrEmpty() {
        return roles == null ? Set.of() : roles;
    }
}
