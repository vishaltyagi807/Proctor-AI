package dev.varshit.proctor.user.imports;

import java.util.Map;
import java.util.Set;

/**
 * One parsed row, still in human-friendly form (role names, department codes) - resolved to a
 * real {@code CreateUserRequest} per-row during the import loop, once the reference lookups are
 * available.
 */
public record ImportRow(
        String name,
        String email,
        String password,
        Set<String> roleNames,
        Set<String> departmentCodes,
        Map<String, Object> customFields,
        Boolean enabled,
        Boolean verified
) {
}
