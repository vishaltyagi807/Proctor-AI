package dev.varshit.proctor.storageservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.s3")
public record S3Properties(
        String endpoint,
        String publicEndpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        Duration uploadUrlTtl,
        Duration downloadUrlTtl
) {
}
