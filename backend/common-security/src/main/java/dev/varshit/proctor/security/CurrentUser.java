package dev.varshit.proctor.security;

import dev.varshit.proctor.common.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static Mono<UserPrincipal> require() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .filter(UserPrincipal.class::isInstance)
                .cast(UserPrincipal.class)
                .switchIfEmpty(Mono.error(new UnauthorizedException("Not logged in")));
    }
}
