package dev.varshit.proctor.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record PatchUserRequest(
        @Size(min = 1, max = 200) String name,
        @Size(min = 8, max = 200) String password,
        @Email @Size(max = 320) String email,
        Boolean enabled,
        Boolean verified,
        Set<UUID> departments,
        Set<UUID> roles,
        Map<String, Object> customFields
) {
}
