package dev.varshit.proctor.security.password;

import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class BcryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder encoder;

    public BcryptPasswordHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public Mono<String> hash(String raw) {
        return Mono.fromCallable(() -> encoder.encode(raw)).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> matches(String raw, String hash) {
        return Mono.fromCallable(() -> encoder.matches(raw, hash)).subscribeOn(Schedulers.boundedElastic());
    }
}
