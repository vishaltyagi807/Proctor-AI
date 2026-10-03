package dev.varshit.proctor.monitor.dto;

import java.util.List;

public record SystemSnapshot(
        long timestamp,
        HostStats host,
        List<ServiceStats> services,
        DatabaseView database,
        RedisStats redis,
        StorageStats storage,
        HistoryPoint point,
        List<String> issues
) {
}
