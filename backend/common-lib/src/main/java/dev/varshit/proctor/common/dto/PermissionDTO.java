package dev.varshit.proctor.common.dto;

import dev.varshit.proctor.common.enums.EntityType;
import dev.varshit.proctor.common.enums.PermissionAction;
import dev.varshit.proctor.common.enums.PermissionScope;

import java.util.UUID;

public record PermissionDTO(
        UUID id,
        EntityType entity,
        PermissionAction action,
        PermissionScope scope,
        String description
) {
}
