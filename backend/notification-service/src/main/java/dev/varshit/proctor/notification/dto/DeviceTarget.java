package dev.varshit.proctor.notification.dto;

import java.util.UUID;

public record DeviceTarget(UUID id, String platform, String token) {
}
