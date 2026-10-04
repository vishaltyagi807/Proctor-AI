package dev.varshit.proctor.discovery_server.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "discovery.security")
public record DiscoveryCredentials(
        @NotBlank(message = "DISCOVERY_USERNAME is required") String username,
        @NotBlank(message = "DISCOVERY_PASSWORD is required")
        @Size(min = 32, message = "DISCOVERY_PASSWORD must be at least 32 characters") String password) {

    @Override
    public String toString() {
        return "DiscoveryCredentials[username=" + username + ", password=****]";
    }
}
