package dev.varshit.proctor.storageservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UploadUrlRequest(
        @NotBlank @Size(max = 200) String fileName,
        @NotBlank @Size(max = 150) String contentType,
        @Positive long sizeBytes
) {
}
