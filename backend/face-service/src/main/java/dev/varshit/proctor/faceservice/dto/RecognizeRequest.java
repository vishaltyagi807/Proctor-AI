package dev.varshit.proctor.faceservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RecognizeRequest(@NotBlank String image, @Pattern(regexp = "upload|camera") String source) {

    public boolean live() {
        return "camera".equals(source);
    }
}
