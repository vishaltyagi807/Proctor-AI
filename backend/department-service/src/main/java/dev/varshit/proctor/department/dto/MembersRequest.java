package dev.varshit.proctor.department.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;
import java.util.UUID;

public record MembersRequest(@NotEmpty Set<UUID> userIds) {
}
