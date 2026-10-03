package dev.varshit.proctor.common.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UserInfoDTO(
        UUID id,
        String email,
        String name,
        boolean enabled,
        boolean verified,
        Map<String, Object> customFields,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<RoleDTO> roles,
        List<DepartmentDTO> departments
) {
}
