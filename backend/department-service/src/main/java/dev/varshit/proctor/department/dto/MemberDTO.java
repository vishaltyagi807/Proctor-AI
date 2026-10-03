package dev.varshit.proctor.department.dto;

import java.util.UUID;

public record MemberDTO(UUID id, String email, String name) {
}
