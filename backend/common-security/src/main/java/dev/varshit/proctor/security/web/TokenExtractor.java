package dev.varshit.proctor.security.web;

import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

import java.util.Optional;

public final class TokenExtractor {

    public static final String COOKIE_NAME = "token";
    private static final String BEARER = "Bearer ";

    private TokenExtractor() {
    }

    public static Optional<String> extract(ServerWebExchange exchange) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(COOKIE_NAME);
        if (cookie != null && !cookie.getValue().isBlank()) {
            return Optional.of(cookie.getValue());
        }
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER) && header.length() > BEARER.length()) {
            return Optional.of(header.substring(BEARER.length()).trim());
        }
        return Optional.empty();
    }
}
