package dev.varshit.proctor.complaint.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignRequest(@NotNull UUID assignedTo) {
}
