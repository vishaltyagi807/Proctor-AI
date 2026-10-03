package dev.varshit.proctor.notification.dto;

import jakarta.validation.constraints.Size;

public record UpdateFcmRequest(
        Boolean enabled,
        @Size(max = 20000) String serviceAccountJson
) {
}
