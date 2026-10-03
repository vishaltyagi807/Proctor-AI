package dev.varshit.proctor.complaint.dto;

import dev.varshit.proctor.common.enums.ComplaintPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record CreateComplaintRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String description,
        @Size(max = 100) String category,
        ComplaintPriority priority,
        UUID departmentId,
        UUID studentId,
        @Size(max = 50) List<UUID> subjectIds,
        Map<String, Object> customFields
) {
    public List<UUID> otherSubjects(UUID student) {
        if (subjectIds == null) {
            return List.of();
        }
        return subjectIds.stream().filter(Objects::nonNull).filter(id -> !id.equals(student)).distinct().toList();
    }

    public String categoryOrDefault() {
        return category == null || category.isBlank() ? "general" : category.trim();
    }

    public ComplaintPriority priorityOrDefault() {
        return priority == null ? ComplaintPriority.medium : priority;
    }
}
