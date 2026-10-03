package dev.varshit.proctor.monitor.dto;

public record RedisStats(
        String version,
        String role,
        long uptimeSeconds,
        long connectedClients,
        long blockedClients,
        long usedMemory,
        long peakMemory,
        long maxMemory,
        long opsPerSecond,
        double hitRatio,
        long keys,
        long expiringKeys,
        long evictedKeys,
        double inputKbps,
        double outputKbps
) {
}
