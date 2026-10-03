package dev.varshit.proctor.monitor.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record DatabaseView(
        String version,
        OffsetDateTime startedAt,
        long sizeBytes,
        int maxConnections,
        int connections,
        int active,
        int idle,
        int idleInTransaction,
        int waiting,
        double longestQuerySeconds,
        double transactionRate,
        double rollbackRate,
        double cacheHitRatio,
        double rowsReadRate,
        double rowsWrittenRate,
        long deadlocks,
        long tempBytes,
        List<DatabaseStats.TableSize> tables
) {
}
