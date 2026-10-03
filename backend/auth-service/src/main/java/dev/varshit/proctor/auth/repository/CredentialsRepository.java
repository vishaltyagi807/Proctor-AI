package dev.varshit.proctor.auth.repository;

import dev.varshit.proctor.auth.dto.Credentials;
import reactor.core.publisher.Mono;

public interface CredentialsRepository {

    Mono<Credentials> findByEmail(String email);

    Mono<Credentials> findById(java.util.UUID id);
}
