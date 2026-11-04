package dev.varshit.proctor.auth.service;

import dev.varshit.proctor.auth.config.LoginProtectionProperties;
import dev.varshit.proctor.common.exception.TooManyRequestsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Component
@EnableConfigurationProperties(LoginProtectionProperties.class)
public class LoginAttemptGuard {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptGuard.class);
    private static final String PREFIX = "auth:login-failures:";

    private final ReactiveStringRedisTemplate redis;
    private final LoginProtectionProperties properties;

    public LoginAttemptGuard(ReactiveStringRedisTemplate redis, LoginProtectionProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public Mono<Void> check(String email) {
        return redis.opsForValue().get(key(email))
                .map(Long::parseLong)
                .onErrorResume(e -> unavailable("read", e).then(Mono.empty()))
                .filter(failures -> failures >= properties.maxFailures())
                .flatMap(failures -> Mono.<Void>error(new TooManyRequestsException(
                        "Too many failed sign-in attempts. Try again in " + minutes() + " minutes.")))
                .then();
    }

    public Mono<Void> recordFailure(String email) {
        String key = key(email);
        return redis.opsForValue().increment(key)
                .flatMap(count -> redis.expire(key, properties.lockout()))
                .then()
                .onErrorResume(e -> unavailable("record", e));
    }

    public Mono<Void> reset(String email) {
        return redis.delete(key(email)).then().onErrorResume(e -> unavailable("reset", e));
    }

    private long minutes() {
        return Math.max(1, properties.lockout().toMinutes());
    }

    private static Mono<Void> unavailable(String action, Throwable e) {
        log.warn("Could not {} sign-in failures in Redis, continuing without the account lockout: {}", action, e.toString());
        return Mono.empty();
    }

    static String key(String email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(email.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
