package dev.varshit.proctor.auth.dto;

import dev.varshit.proctor.common.dto.UserDTO;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record Credentials(
        UUID id,
        String email,
        String name,
        String password,
        boolean enabled,
        boolean verified,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public UserDTO toUser() {
        return new UserDTO(id, email, name, enabled, verified, Map.of(), createdAt, updatedAt);
    }
}
