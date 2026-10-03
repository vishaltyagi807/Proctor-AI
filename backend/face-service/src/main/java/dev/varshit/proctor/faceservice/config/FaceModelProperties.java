package dev.varshit.proctor.faceservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.face")
public record FaceModelProperties(
        String detectionModelPath,
        String embeddingModelPath,
        int detectionInputSize,
        float detectionScoreThreshold,
        float detectionNmsThreshold,
        float matchThreshold,
        long maxImageBytes,
        int selfEnrollLimit,
        long maxZipBytes
) {
}
