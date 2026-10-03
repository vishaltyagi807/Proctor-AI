package dev.varshit.proctor.monitor.dto;

import java.util.List;

public record HostStats(
        String hostname,
        String os,
        String cpuModel,
        int physicalCores,
        int logicalCores,
        long uptimeSeconds,
        int processes,
        int threads,
        double cpu,
        List<Double> cores,
        List<Double> loadAverage,
        long memoryTotal,
        long memoryUsed,
        long swapTotal,
        long swapUsed,
        List<Disk> disks,
        long diskReadRate,
        long diskWriteRate,
        List<Network> networks,
        long networkRxRate,
        long networkTxRate
) {

    public record Disk(String mount, String type, long total, long used) {
    }

    public record Network(String name, String address, long rxRate, long txRate, long speed) {
    }
}
