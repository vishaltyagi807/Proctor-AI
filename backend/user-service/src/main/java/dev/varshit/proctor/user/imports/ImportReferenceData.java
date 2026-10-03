package dev.varshit.proctor.user.imports;

import dev.varshit.proctor.common.enums.CustomFieldType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lookup tables built once per import (or template download) so every row/column can be
 * resolved from human-friendly keys - role name, department code, custom field key - instead of
 * raw UUIDs.
 */
public record ImportReferenceData(
        Map<String, UUID> roleIdsByName,
        Map<String, UUID> departmentIdsByCode,
        Map<String, CustomFieldMeta> customFieldsByKey
) {

    public record CustomFieldMeta(CustomFieldType dataType, List<String> options) {
    }
}
