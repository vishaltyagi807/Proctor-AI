package dev.varshit.proctor.customfield.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record PatchCustomFieldRequest(
        @Size(min = 1, max = 200) String label,
        @Size(max = 500) String helpText,
        Boolean required,
        @Size(max = 100) List<@Size(min = 1, max = 200) String> options,
        Integer sortOrder,
        Boolean active
) {
}
