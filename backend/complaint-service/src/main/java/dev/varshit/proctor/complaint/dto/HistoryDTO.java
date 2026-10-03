package dev.varshit.proctor.complaint.dto;

import dev.varshit.proctor.common.enums.ComplaintStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record HistoryDTO(
        UUID id,
        UUID complaintId,
        UUID actorId,
        String actorName,
        ComplaintStatus fromStatus,
        ComplaintStatus toStatus,
        String note,
        OffsetDateTime createdAt
) {
}
