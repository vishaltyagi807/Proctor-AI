package dev.varshit.proctor.storageservice.dto;

import java.time.OffsetDateTime;

public record DownloadUrlResponse(String url, String fileName, String contentType, OffsetDateTime expiresAt) {
}
