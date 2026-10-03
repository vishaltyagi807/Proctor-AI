package dev.varshit.proctor.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String dir, Long maxBytes) {

    public StorageProperties {
        dir = dir == null || dir.isBlank() ? "./storage" : dir;
        maxBytes = maxBytes == null ? 10L * 1024 * 1024 : maxBytes;
    }
}
