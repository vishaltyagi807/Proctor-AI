package dev.varshit.proctor.complaint.dto;

import dev.varshit.proctor.common.enums.ComplaintPriority;
import dev.varshit.proctor.common.enums.ComplaintStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ComplaintDTO(
        UUID id,
        String title,
        String description,
        String category,
        ComplaintPriority priority,
        ComplaintStatus status,
        UUID studentId,
        String studentName,
        List<ComplaintSubjectDTO> subjects,
        UUID assignedTo,
        String assignedToName,
        UUID departmentId,
        String departmentName,
        String resolution,
        OffsetDateTime resolvedAt,
        UUID raisedBy,
        String raisedByName,
        List<String> raisedByDepartments,
        boolean reporterVisible,
        boolean revealReporter,
        Map<String, Object> customFields,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
