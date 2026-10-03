package dev.varshit.proctor.security.jwt;

import dev.varshit.proctor.security.UserPrincipal;

import java.util.Optional;

public interface TokenService {

    IssuedToken issue(UserPrincipal principal);

    Optional<UserPrincipal> verify(String token);
}
