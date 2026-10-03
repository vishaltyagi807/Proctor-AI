package dev.varshit.proctor.complaint.dto;

import dev.varshit.proctor.common.enums.ComplaintPriority;
import dev.varshit.proctor.common.enums.ComplaintStatus;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record PatchComplaintRequest(
        @Size(min = 1, max = 200) String title,
        @Size(min = 1, max = 5000) String description,
        @Size(min = 1, max = 100) String category,
        ComplaintPriority priority,
        ComplaintStatus status,
        UUID assignedTo,
        @Size(max = 5000) String resolution,
        @Size(max = 1000) String note,
        Boolean revealReporter,
        Map<String, Object> customFields
) {
}
