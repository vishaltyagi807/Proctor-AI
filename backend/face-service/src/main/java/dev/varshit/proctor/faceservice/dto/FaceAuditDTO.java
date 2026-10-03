package dev.varshit.proctor.faceservice.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FaceAuditDTO(
        UUID id,
        String event,
        UUID actorId,
        String actorName,
        UUID subjectId,
        String subjectName,
        Boolean matched,
        Float confidence,
        Integer facesDetected,
        String detail,
        OffsetDateTime createdAt) {
}
