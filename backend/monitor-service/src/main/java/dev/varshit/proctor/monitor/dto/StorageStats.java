package dev.varshit.proctor.monitor.dto;

public record StorageStats(long files, long bytes, long pendingFiles, long evidenceFiles) {
}
