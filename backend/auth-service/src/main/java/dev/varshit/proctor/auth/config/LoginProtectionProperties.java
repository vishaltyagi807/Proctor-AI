package dev.varshit.proctor.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.login")
public record LoginProtectionProperties(int maxFailures, Duration lockout) {

    public LoginProtectionProperties {
        if (maxFailures < 1) {
            throw new IllegalStateException("app.login.max-failures must be at least 1");
        }
        if (lockout == null || lockout.isNegative() || lockout.isZero()) {
            throw new IllegalStateException("app.login.lockout must be a positive duration");
        }
    }
}
