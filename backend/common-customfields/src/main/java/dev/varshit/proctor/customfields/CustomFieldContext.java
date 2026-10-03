package dev.varshit.proctor.customfields;

import java.util.Set;
import java.util.UUID;

public record CustomFieldContext(Set<UUID> roleIds, Set<UUID> departmentIds) {

    public static CustomFieldContext empty() {
        return new CustomFieldContext(Set.of(), Set.of());
    }

    public static CustomFieldContext ofDepartment(UUID departmentId) {
        return new CustomFieldContext(Set.of(), departmentId == null ? Set.of() : Set.of(departmentId));
    }

    public static CustomFieldContext ofRole(UUID roleId) {
        return new CustomFieldContext(Set.of(roleId), Set.of());
    }
}
