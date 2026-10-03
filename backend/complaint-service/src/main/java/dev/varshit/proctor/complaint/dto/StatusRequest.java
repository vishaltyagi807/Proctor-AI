package dev.varshit.proctor.complaint.dto;

import dev.varshit.proctor.common.enums.ComplaintStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusRequest(
        @NotNull ComplaintStatus status,
        @Size(max = 5000) String resolution,
        @Size(max = 1000) String note
) {
}
