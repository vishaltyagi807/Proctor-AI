package dev.varshit.proctor.auth.dto;

import jakarta.validation.constraints.Size;

public record RefreshRequest(@Size(max = 200) String refreshToken) {
}
