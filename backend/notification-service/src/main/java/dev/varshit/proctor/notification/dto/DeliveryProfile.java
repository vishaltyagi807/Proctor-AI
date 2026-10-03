package dev.varshit.proctor.notification.dto;

import java.util.List;

public record DeliveryProfile(boolean pushEnabled, boolean realtimeEnabled, List<String> mutedTypes) {

    public boolean muted(String type) {
        return mutedTypes != null && mutedTypes.contains(type);
    }
}
