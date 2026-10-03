package dev.varshit.proctor.persistence.rls;

import dev.varshit.proctor.security.UserPrincipal;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Supplier;

public interface SecureTransaction {

    <T> Mono<T> mono(Supplier<Mono<T>> work);

    <T> Flux<T> flux(Supplier<Flux<T>> work);

    <T> Mono<T> monoAs(UserPrincipal principal, Supplier<Mono<T>> work);
}
