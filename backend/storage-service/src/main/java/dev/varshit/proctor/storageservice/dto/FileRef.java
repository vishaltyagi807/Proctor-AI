package dev.varshit.proctor.storageservice.dto;

import java.util.UUID;

public record FileRef(
        UUID id,
        UUID complaintId,
        String fileName,
        String contentType,
        long sizeBytes,
        String fileUrl,
        String status
) {
}
