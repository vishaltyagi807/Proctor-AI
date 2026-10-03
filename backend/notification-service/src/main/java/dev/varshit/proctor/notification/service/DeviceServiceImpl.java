package dev.varshit.proctor.notification.service;

import dev.varshit.proctor.common.exception.NotFoundException;
import dev.varshit.proctor.notification.dto.DeviceDTO;
import dev.varshit.proctor.notification.dto.PreferencesDTO;
import dev.varshit.proctor.notification.dto.RegisterDeviceRequest;
import dev.varshit.proctor.notification.repository.DeviceRepository;
import dev.varshit.proctor.persistence.rls.SecureTransaction;
import dev.varshit.proctor.security.CurrentUser;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class DeviceServiceImpl implements DeviceService {

    private final DeviceRepository repository;
    private final SecureTransaction transaction;

    public DeviceServiceImpl(DeviceRepository repository, SecureTransaction transaction) {
        this.repository = repository;
        this.transaction = transaction;
    }

    @Override
    public Mono<DeviceDTO> register(RegisterDeviceRequest request) {
        return transaction.mono(() -> repository.register(request.platform(), request.token().trim(), request.deviceName())
                .flatMap(repository::findById)
                .switchIfEmpty(Mono.error(new NotFoundException("Device not found"))));
    }

    @Override
    public Flux<DeviceDTO> list() {
        return transaction.flux(repository::findMine);
    }

    @Override
    public Mono<Void> remove(UUID id) {
        return transaction.mono(() -> repository.delete(id).flatMap(rows -> rows == 0
                ? Mono.<Void>error(new NotFoundException("Device not found")) : Mono.empty()));
    }

    @Override
    public Mono<PreferencesDTO> preferences() {
        return CurrentUser.require().flatMap(principal ->
                transaction.monoAs(principal, () -> repository.preferences(principal.id())));
    }

    @Override
    public Mono<PreferencesDTO> updatePreferences(PreferencesDTO preferences) {
        return CurrentUser.require().flatMap(principal -> transaction.monoAs(principal,
                () -> repository.savePreferences(principal.id(), preferences)
                        .then(repository.preferences(principal.id()))));
    }
}
