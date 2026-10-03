package dev.varshit.proctor.notification.repository;

import dev.varshit.proctor.notification.dto.DeviceDTO;
import dev.varshit.proctor.notification.dto.DeviceTarget;
import dev.varshit.proctor.notification.dto.PreferencesDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface DeviceRepository {

    Mono<UUID> register(String platform, String token, String name);

    Mono<DeviceDTO> findById(UUID id);

    Flux<DeviceDTO> findMine();

    Flux<DeviceTarget> findMyTargets();

    Mono<Long> delete(UUID id);

    Mono<PreferencesDTO> preferences(UUID userId);

    Mono<Long> savePreferences(UUID userId, PreferencesDTO preferences);
}
