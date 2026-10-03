package dev.varshit.proctor.storageservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.storage-limits")
public record StorageLimits(
        long maxFileBytes,
        int maxFilesPerComplaint,
        long maxBytesPerComplaint,
        Duration pendingTtl
) {
}
