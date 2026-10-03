package dev.varshit.proctor.auth.service;

import dev.varshit.proctor.auth.dto.ClientContext;
import dev.varshit.proctor.auth.dto.LoginRequest;
import dev.varshit.proctor.auth.dto.LoginResult;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AuthService {

    Mono<LoginResult> login(LoginRequest request, ClientContext client);

    Mono<LoginResult> refresh(String refreshToken, ClientContext client);

    Mono<Void> logout(String refreshToken);

    Mono<Void> logoutAll(UUID userId);
}
