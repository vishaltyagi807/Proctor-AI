package dev.varshit.proctor.security.web;

import dev.varshit.proctor.security.UserPrincipal;
import dev.varshit.proctor.security.jwt.TokenService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

public class JwtAuthenticationWebFilter implements WebFilter {

    private final TokenService tokenService;

    public JwtAuthenticationWebFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return TokenExtractor.extract(exchange)
                .flatMap(token -> tokenService.verify(token).map(principal -> authenticate(principal, token)))
                .map(auth -> chain.filter(exchange).contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .orElseGet(() -> chain.filter(exchange));
    }

    private Authentication authenticate(UserPrincipal principal, String token) {
        return new UsernamePasswordAuthenticationToken(principal, token, List.of());
    }
}
