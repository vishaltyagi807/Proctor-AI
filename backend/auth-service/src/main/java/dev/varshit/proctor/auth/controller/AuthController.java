package dev.varshit.proctor.auth.controller;

import dev.varshit.proctor.auth.config.AuthCookies;
import dev.varshit.proctor.auth.dto.ClientContext;
import dev.varshit.proctor.auth.dto.LoginRequest;
import dev.varshit.proctor.auth.dto.LoginResponse;
import dev.varshit.proctor.auth.dto.LoginResult;
import dev.varshit.proctor.auth.dto.RefreshRequest;
import dev.varshit.proctor.auth.service.AuthService;
import dev.varshit.proctor.common.dto.AccessTokenDTO;
import dev.varshit.proctor.common.exception.UnauthorizedException;
import dev.varshit.proctor.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String DELIVERY_HEADER = "X-Token-Delivery";
    private static final int MAX_AGENT_LENGTH = 255;

    private final AuthService authService;
    private final AuthCookies cookies;

    public AuthController(AuthService authService, AuthCookies cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                     ServerWebExchange exchange) {
        return authService.login(request, client(exchange)).map(result -> respond(result, exchange));
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<LoginResponse>> refresh(@RequestBody(required = false) @Valid RefreshRequest body,
                                                       ServerWebExchange exchange) {
        String token = body != null && body.refreshToken() != null && !body.refreshToken().isBlank()
                ? body.refreshToken() : refreshCookie(exchange);
        if (token == null) {
            return Mono.error(new UnauthorizedException("Refresh token is missing"));
        }
        return authService.refresh(token, client(exchange)).map(result -> respond(result, exchange));
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Map<String, String>>> logout(@RequestBody(required = false) @Valid RefreshRequest body,
                                                            ServerWebExchange exchange) {
        String token = body != null && body.refreshToken() != null ? body.refreshToken() : refreshCookie(exchange);
        return authService.logout(token).then(Mono.fromSupplier(() -> cleared("Logout successful")));
    }

    @PostMapping("/logout-all")
    public Mono<ResponseEntity<Map<String, String>>> logoutAll() {
        return CurrentUser.require()
                .flatMap(principal -> authService.logoutAll(principal.id()))
                .then(Mono.fromSupplier(() -> cleared("Logged out from all devices")));
    }

    private ResponseEntity<LoginResponse> respond(LoginResult result, ServerWebExchange exchange) {
        boolean body = "body".equalsIgnoreCase(exchange.getRequest().getHeaders().getFirst(DELIVERY_HEADER));
        var access = result.access();
        var refresh = result.refresh();
        AccessTokenDTO info = new AccessTokenDTO(
                access.expiresIn(), access.issuedAt(), access.expiresAt(),
                refresh.expiresIn(), refresh.expiresAt(),
                body ? access.value() : null, body ? refresh.value() : null);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.access(access.value(), Duration.ofSeconds(access.expiresIn())).toString())
                .header(HttpHeaders.SET_COOKIE, cookies.refresh(refresh.value(), Duration.ofSeconds(refresh.expiresIn())).toString())
                .body(new LoginResponse(result.user(), info));
    }

    private ResponseEntity<Map<String, String>> cleared(String message) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.clearAccess().toString())
                .header(HttpHeaders.SET_COOKIE, cookies.clearRefresh().toString())
                .body(Map.of("message", message));
    }

    private String refreshCookie(ServerWebExchange exchange) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(AuthCookies.REFRESH_COOKIE);
        return cookie == null || cookie.getValue().isBlank() ? null : cookie.getValue();
    }

    private ClientContext client(ServerWebExchange exchange) {
        String agent = exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT);
        if (agent != null && agent.length() > MAX_AGENT_LENGTH) {
            agent = agent.substring(0, MAX_AGENT_LENGTH);
        }
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : exchange.getRequest().getRemoteAddress() == null ? null
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        return new ClientContext(agent, ip);
    }
}
