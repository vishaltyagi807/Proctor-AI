package dev.varshit.proctor.security.password;

import reactor.core.publisher.Mono;

public interface PasswordHasher {

    Mono<String> hash(String raw);

    Mono<Boolean> matches(String raw, String hash);
}
