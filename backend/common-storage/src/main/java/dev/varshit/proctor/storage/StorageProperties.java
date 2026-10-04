package dev.varshit.proctor.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String dir, Long maxBytes, Duration tempTtl, Duration sweepInterval) {

    public StorageProperties {
        dir = dir == null || dir.isBlank() ? "./storage" : dir;
        maxBytes = maxBytes == null ? 10L * 1024 * 1024 : maxBytes;
        tempTtl = tempTtl == null ? Duration.ofMinutes(30) : tempTtl;
        sweepInterval = sweepInterval == null ? Duration.ofMinutes(15) : sweepInterval;
    }
}
