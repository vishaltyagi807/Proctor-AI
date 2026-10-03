package dev.varshit.proctor.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterDeviceRequest(
        @NotBlank @Pattern(regexp = "android|ios|web", message = "must be android, ios or web") String platform,
        @NotBlank @Size(min = 20, max = 4096) String token,
        @Size(max = 200) String deviceName
) {
}
