package dev.varshit.proctor.complaint.dto;

import java.util.List;
import java.util.UUID;

public record ComplaintParties(UUID studentId, UUID raisedBy, UUID assignedTo, UUID departmentId, String title,
                               List<UUID> subjectIds) {

    public List<UUID> subjectIdsOrEmpty() {
        return subjectIds == null ? List.of() : subjectIds;
    }
}
