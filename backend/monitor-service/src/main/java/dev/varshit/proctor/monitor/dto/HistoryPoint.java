package dev.varshit.proctor.monitor.dto;

import java.util.Map;

public record HistoryPoint(
        long timestamp,
        double cpu,
        double memory,
        long networkRx,
        long networkTx,
        long diskRead,
        long diskWrite,
        double requests,
        double errors,
        int dbConnections,
        double dbTransactions,
        long redisOps,
        Map<String, Double> serviceCpu,
        Map<String, Long> serviceHeap
) {
}
