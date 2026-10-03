package dev.varshit.proctor.complaint.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CommentDTO(
        UUID id,
        UUID complaintId,
        UUID authorId,
        String authorName,
        String body,
        boolean internal,
        OffsetDateTime createdAt
) {
}
