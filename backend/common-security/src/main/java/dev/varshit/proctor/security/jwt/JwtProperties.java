package dev.varshit.proctor.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, String issuer, long accessTokenExpiresIn, long refreshTokenExpiresIn) {
}
