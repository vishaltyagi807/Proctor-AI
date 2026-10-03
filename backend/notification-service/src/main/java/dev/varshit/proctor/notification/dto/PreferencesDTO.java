package dev.varshit.proctor.notification.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record PreferencesDTO(
        boolean pushEnabled,
        boolean realtimeEnabled,
        @Size(max = 100) List<@Size(max = 100) String> mutedTypes
) {
}
