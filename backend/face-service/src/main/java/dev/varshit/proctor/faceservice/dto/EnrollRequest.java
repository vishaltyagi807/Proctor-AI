package dev.varshit.proctor.faceservice.dto;

import jakarta.validation.constraints.NotBlank;

public record EnrollRequest(@NotBlank String image) {
}
