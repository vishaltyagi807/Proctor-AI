package dev.varshit.proctor.monitor.dto;

public record ServiceStats(
        String service,
        String instanceId,
        String host,
        int port,
        String status,
        boolean registered,
        Long lastSeenMs,
        Long uptimeMs,
        Double cpu,
        Long heapUsed,
        Long heapMax,
        Long nonHeapUsed,
        Long rss,
        Integer threads,
        Double requestRate,
        Double errorRate,
        Double latencyMs,
        Double gcRate,
        Integer dbPoolAcquired,
        Integer dbPoolPending,
        Integer dbPoolMax
) {
}
