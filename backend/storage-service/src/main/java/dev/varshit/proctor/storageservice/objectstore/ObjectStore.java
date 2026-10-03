package dev.varshit.proctor.storageservice.objectstore;

import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.Map;

public interface ObjectStore {

    PresignedRequest presignUpload(String key, String contentType, long sizeBytes);

    PresignedRequest presignDownload(String key, String fileName, String contentType, boolean inline);

    Mono<ObjectMetadata> stat(String key);

    Mono<Void> delete(String key);

    Mono<Void> ensureBucket();

    record PresignedRequest(String url, String method, Map<String, String> headers, OffsetDateTime expiresAt) {
    }

    record ObjectMetadata(long sizeBytes, String contentType) {
    }
}
