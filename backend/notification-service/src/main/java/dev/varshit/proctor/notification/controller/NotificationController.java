package dev.varshit.proctor.notification.controller;

import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.notification.dto.DeviceDTO;
import dev.varshit.proctor.notification.dto.NotificationDTO;
import dev.varshit.proctor.notification.dto.PreferencesDTO;
import dev.varshit.proctor.notification.dto.RegisterDeviceRequest;
import dev.varshit.proctor.notification.dto.SendNotificationRequest;
import dev.varshit.proctor.notification.service.DeviceService;
import dev.varshit.proctor.notification.service.NotificationService;
import dev.varshit.proctor.persistence.search.PageParams;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notifications;
    private final DeviceService devices;

    public NotificationController(NotificationService notifications, DeviceService devices) {
        this.notifications = notifications;
        this.devices = devices;
    }

    @GetMapping
    public Mono<PageResponse<NotificationDTO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam Map<String, String> params
    ) {
        return notifications.list(params, PageParams.of(page, size, sortBy, direction));
    }

    @GetMapping("/unread-count")
    public Mono<Map<String, Long>> unreadCount() {
        return notifications.unreadCount().map(count -> Map.of("unread", count));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream() {
        return notifications.stream();
    }

    @PostMapping("/read-all")
    public Mono<Map<String, Long>> readAll() {
        return notifications.markAllRead().map(count -> Map.of("updated", count));
    }

    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> markRead(@PathVariable UUID id) {
        return notifications.markRead(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable UUID id) {
        return notifications.delete(id);
    }

    @PostMapping("/send")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<dev.varshit.proctor.notification.dto.SendResult> send(@Valid @RequestBody SendNotificationRequest request) {
        return notifications.send(request);
    }

    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<DeviceDTO> registerDevice(@Valid @RequestBody RegisterDeviceRequest request) {
        return devices.register(request);
    }

    @GetMapping("/devices")
    public Flux<DeviceDTO> devices() {
        return devices.list();
    }

    @DeleteMapping("/devices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> removeDevice(@PathVariable UUID id) {
        return devices.remove(id);
    }

    @GetMapping("/preferences")
    public Mono<PreferencesDTO> preferences() {
        return devices.preferences();
    }

    @PutMapping("/preferences")
    public Mono<PreferencesDTO> updatePreferences(@Valid @RequestBody PreferencesDTO preferences) {
        return devices.updatePreferences(preferences);
    }
}
