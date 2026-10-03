package dev.varshit.proctor.storageservice.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FileDTO(
        UUID id,
        UUID complaintId,
        UUID uploadedBy,
        String uploadedByName,
        String fileName,
        String contentType,
        long sizeBytes,
        String kind,
        String status,
        OffsetDateTime createdAt
) {
}
