package dev.varshit.proctor.notification.dto;

import dev.varshit.proctor.notifications.NotificationPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record SendNotificationRequest(
        @Size(max = 1000) Set<UUID> userIds,
        @Size(max = 100) Set<UUID> roleIds,
        @Size(max = 100) Set<UUID> departmentIds,
        @NotBlank @Size(max = 100) String type,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String body,
        Map<String, String> data,
        NotificationPriority priority,
        Boolean includeSender
) {
    public Set<UUID> users() {
        return userIds == null ? Set.of() : userIds;
    }

    public Set<UUID> roles() {
        return roleIds == null ? Set.of() : roleIds;
    }

    public Set<UUID> departments() {
        return departmentIds == null ? Set.of() : departmentIds;
    }

    public boolean hasTargets() {
        return !users().isEmpty() || !roles().isEmpty() || !departments().isEmpty();
    }
}
