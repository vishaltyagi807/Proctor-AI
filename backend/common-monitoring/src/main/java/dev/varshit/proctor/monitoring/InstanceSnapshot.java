package dev.varshit.proctor.monitoring;

public record InstanceSnapshot(
        String service,
        String instanceId,
        String host,
        String address,
        int port,
        long pid,
        long startedAt,
        long timestamp,
        Cpu cpu,
        Memory memory,
        Threads threads,
        Gc gc,
        long classesLoaded,
        Http http,
        DbPool dbPool
) {

    public record Cpu(double process, double system, int processors, double loadAverage) {
    }

    public record Memory(long heapUsed, long heapCommitted, long heapMax, long nonHeapUsed, long nonHeapCommitted,
                         long directUsed, Long rss) {
    }

    public record Threads(int live, int daemon, int peak) {
    }

    public record Gc(long collections, long timeMs) {
    }

    public record Http(long requests, long errors, double totalTimeMs) {
    }

    public record DbPool(int acquired, int idle, int pending, int max) {
    }
}
