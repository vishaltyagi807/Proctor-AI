package dev.varshit.proctor.auth.service;

import dev.varshit.proctor.auth.dto.ClientContext;
import dev.varshit.proctor.auth.dto.Credentials;
import dev.varshit.proctor.auth.dto.LoginRequest;
import dev.varshit.proctor.auth.dto.LoginResult;
import dev.varshit.proctor.auth.repository.CredentialsRepository;
import dev.varshit.proctor.common.exception.UnauthorizedException;
import dev.varshit.proctor.security.UserPrincipal;
import dev.varshit.proctor.security.jwt.TokenService;
import dev.varshit.proctor.security.password.PasswordHasher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String INVALID = "Invalid email or password";
    private static final String REFRESH_INVALID = "Refresh token is invalid or expired";

    private final CredentialsRepository repository;
    private final PasswordHasher hasher;
    private final TokenService tokenService;
    private final DatabaseRefreshTokenService refreshTokens;
    private final Mono<String> decoyHash;

    public AuthServiceImpl(CredentialsRepository repository, PasswordHasher hasher, TokenService tokenService,
                           DatabaseRefreshTokenService refreshTokens) {
        this.repository = repository;
        this.hasher = hasher;
        this.tokenService = tokenService;
        this.refreshTokens = refreshTokens;
        this.decoyHash = hasher.hash("decoy-password-for-timing").cache();
    }

    @Override
    public Mono<LoginResult> login(LoginRequest request, ClientContext client) {
        return repository.findByEmail(request.email().trim())
                .flatMap(credentials -> verify(request.password(), credentials, client))
                .switchIfEmpty(Mono.defer(() -> decoyHash
                        .flatMap(hash -> hasher.matches(request.password(), hash))
                        .then(Mono.error(new UnauthorizedException(INVALID)))));
    }

    @Override
    public Mono<LoginResult> refresh(String refreshToken, ClientContext client) {
        return refreshTokens.rotate(refreshToken, client).flatMap(rotation -> repository.findById(rotation.userId())
                .filter(Credentials::enabled)
                .switchIfEmpty(Mono.defer(() -> refreshTokens.revokeAll(rotation.userId())
                        .then(Mono.error(new UnauthorizedException(REFRESH_INVALID)))))
                .flatMap(credentials -> refreshTokens.issue(credentials.id(), rotation.familyId(), client)
                        .map(refresh -> new LoginResult(credentials.toUser(), issueAccess(credentials), refresh))));
    }

    @Override
    public Mono<Void> logout(String refreshToken) {
        return refreshToken == null || refreshToken.isBlank() ? Mono.empty() : refreshTokens.revoke(refreshToken);
    }

    @Override
    public Mono<Void> logoutAll(UUID userId) {
        return refreshTokens.revokeAll(userId);
    }

    private Mono<LoginResult> verify(String password, Credentials credentials, ClientContext client) {
        return hasher.matches(password, credentials.password())
                .flatMap(matches -> {
                    if (!matches || !credentials.enabled()) {
                        return Mono.error(new UnauthorizedException(INVALID));
                    }
                    return refreshTokens.start(credentials.id(), client)
                            .map(refresh -> new LoginResult(credentials.toUser(), issueAccess(credentials), refresh));
                });
    }

    private dev.varshit.proctor.security.jwt.IssuedToken issueAccess(Credentials credentials) {
        return tokenService.issue(new UserPrincipal(credentials.id(), credentials.email()));
    }
}
