package dev.varshit.proctor.monitor.repository;

import dev.varshit.proctor.monitor.dto.DatabaseStats;
import dev.varshit.proctor.monitor.dto.StorageStats;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.Map;

@Repository
public class SystemStatsRepository {

    private record DatabaseRow(DatabaseStats stats) {
    }

    private record StorageRow(StorageStats stats) {
    }

    private final SqlGateway gateway;

    public SystemStatsRepository(SqlGateway gateway) {
        this.gateway = gateway;
    }

    public Mono<DatabaseStats> database() {
        return gateway.queryOne("select system_database_stats() as stats", Map.of(), DatabaseRow.class)
                .map(DatabaseRow::stats);
    }

    public Mono<StorageStats> storage() {
        return gateway.queryOne("select system_storage_stats() as stats", Map.of(), StorageRow.class)
                .map(StorageRow::stats);
    }
}
