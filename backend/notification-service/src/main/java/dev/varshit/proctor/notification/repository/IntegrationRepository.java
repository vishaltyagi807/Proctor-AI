package dev.varshit.proctor.notification.repository;

import dev.varshit.proctor.notification.dto.IntegrationRow;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IntegrationRepository {

    Flux<IntegrationRow> findAll();

    Mono<IntegrationRow> find(String provider);

    Mono<Long> clear(String provider);

    Mono<Long> upsert(String provider, boolean enabled, String configJson, String secretCiphertext, UUID updatedBy);
}
