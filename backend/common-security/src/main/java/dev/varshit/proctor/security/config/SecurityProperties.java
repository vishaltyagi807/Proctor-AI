package dev.varshit.proctor.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(List<String> allowedOrigins, List<String> publicPaths) {

    public SecurityProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins;
        publicPaths = publicPaths == null ? List.of() : publicPaths;
    }
}
