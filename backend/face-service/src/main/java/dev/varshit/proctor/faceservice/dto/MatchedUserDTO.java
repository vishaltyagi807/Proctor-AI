package dev.varshit.proctor.faceservice.dto;

import java.util.UUID;

public record MatchedUserDTO(UUID id, String name, String email) {
}
