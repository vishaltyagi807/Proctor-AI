package dev.varshit.proctor.auth.config;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthCookies {

    public static final String ACCESS_COOKIE = "token";
    public static final String REFRESH_COOKIE = "refresh_token";
    private static final String REFRESH_PATH = "/auth";

    private final CookieProperties properties;

    public AuthCookies(CookieProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie access(String value, Duration maxAge) {
        return build(ACCESS_COOKIE, value, "/", maxAge);
    }

    public ResponseCookie refresh(String value, Duration maxAge) {
        return build(REFRESH_COOKIE, value, REFRESH_PATH, maxAge);
    }

    public ResponseCookie clearAccess() {
        return build(ACCESS_COOKIE, "", "/", Duration.ZERO);
    }

    public ResponseCookie clearRefresh() {
        return build(REFRESH_COOKIE, "", REFRESH_PATH, Duration.ZERO);
    }

    private ResponseCookie build(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(properties.sameSite())
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}
