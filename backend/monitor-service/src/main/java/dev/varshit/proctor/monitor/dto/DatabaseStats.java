package dev.varshit.proctor.monitor.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record DatabaseStats(
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
        long commits,
        long rollbacks,
        long blocksRead,
        long blocksHit,
        long rowsReturned,
        long rowsFetched,
        long rowsInserted,
        long rowsUpdated,
        long rowsDeleted,
        long deadlocks,
        long tempBytes,
        List<TableSize> tables
) {

    public record TableSize(String name, long bytes, long rows) {
    }
}
