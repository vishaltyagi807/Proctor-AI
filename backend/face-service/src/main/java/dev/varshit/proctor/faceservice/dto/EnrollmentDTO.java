package dev.varshit.proctor.faceservice.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EnrollmentDTO(
        UUID userId,
        String name,
        String email,
        OffsetDateTime enrolledAt,
        int selfEnrollCount,
        boolean selfEnrollLocked,
        Boolean canReplace,
        Boolean canRemove,
        Boolean canUnlock) {
}
