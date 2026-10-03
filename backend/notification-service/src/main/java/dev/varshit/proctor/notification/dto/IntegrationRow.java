package dev.varshit.proctor.notification.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record IntegrationRow(
        String provider,
        boolean enabled,
        Map<String, Object> config,
        String secretCiphertext,
        UUID updatedBy,
        OffsetDateTime updatedAt
) {
    public IntegrationDTO toDto() {
        return new IntegrationDTO(provider, enabled, secretCiphertext != null && !secretCiphertext.isBlank(),
                config == null ? Map.of() : config, updatedAt);
    }
}
