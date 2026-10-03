package dev.varshit.proctor.notification.service;

import dev.varshit.proctor.notification.dto.DeviceDTO;
import dev.varshit.proctor.notification.dto.PreferencesDTO;
import dev.varshit.proctor.notification.dto.RegisterDeviceRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface DeviceService {

    Mono<DeviceDTO> register(RegisterDeviceRequest request);

    Flux<DeviceDTO> list();

    Mono<Void> remove(UUID id);

    Mono<PreferencesDTO> preferences();

    Mono<PreferencesDTO> updatePreferences(PreferencesDTO preferences);
}
