package dev.varshit.proctor.notification.dto;

import jakarta.validation.constraints.Size;

public record TestPushRequest(
        @Size(max = 4096) String deviceToken,
        @Size(max = 200) String title,
        @Size(max = 500) String body
) {
}
