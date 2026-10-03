package dev.varshit.proctor.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cookie")
public record CookieProperties(boolean secure, String sameSite) {

    public CookieProperties {
        sameSite = sameSite == null || sameSite.isBlank() ? "Lax" : sameSite;
    }
}
