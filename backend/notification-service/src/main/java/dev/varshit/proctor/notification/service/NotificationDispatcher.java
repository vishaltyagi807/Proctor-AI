package dev.varshit.proctor.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.varshit.proctor.notification.dto.DeliveryProfile;
import dev.varshit.proctor.notification.dto.DeviceTarget;
import dev.varshit.proctor.notification.dto.NotificationDTO;
import dev.varshit.proctor.notification.push.FcmCredentials;
import dev.varshit.proctor.notification.push.FcmCredentialsProvider;
import dev.varshit.proctor.notification.push.PushGateway;
import dev.varshit.proctor.notification.push.PushMessage;
import dev.varshit.proctor.notification.push.PushResult;
import dev.varshit.proctor.notification.realtime.RealtimeBroker;
import dev.varshit.proctor.notification.repository.SystemNotificationRepository;
import dev.varshit.proctor.notifications.NotificationEvent;
import dev.varshit.proctor.notifications.NotificationPriority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private static final int RECIPIENT_CONCURRENCY = 8;

    private final SystemNotificationRepository repository;
    private final RealtimeBroker broker;
    private final PushGateway push;
    private final FcmCredentialsProvider credentials;
    private final ObjectMapper mapper;

    public NotificationDispatcher(SystemNotificationRepository repository, RealtimeBroker broker, PushGateway push,
                                  FcmCredentialsProvider credentials, ObjectMapper mapper) {
        this.repository = repository;
        this.broker = broker;
        this.push = push;
        this.credentials = credentials;
        this.mapper = mapper;
    }

    public Mono<Void> dispatch(NotificationEvent event) {
        return Flux.fromIterable(event.recipients())
                .flatMap(userId -> deliver(userId, event).onErrorResume(error -> {
                    log.error("Delivery to {} failed: {}", userId, error.getMessage());
                    return Mono.empty();
                }), RECIPIENT_CONCURRENCY)
                .then();
    }

    private Mono<Void> deliver(UUID userId, NotificationEvent event) {
        return repository.deliveryProfile(userId).flatMap(profile ->
                repository.create(userId, event.type(), event.title(), event.body(), json(event.data()),
                                priority(event), event.source(), expiry(event))
                        .flatMap(notification -> realtime(notification, profile, event)
                                .then(push(notification, profile, event))));
    }

    private Mono<Void> realtime(NotificationDTO notification, DeliveryProfile profile, NotificationEvent event) {
        if (!profile.realtimeEnabled() || profile.muted(event.type())) {
            return repository.logDelivery(notification.id(), "realtime", "skipped", "disabled or muted", null);
        }
        return broker.publish(notification.userId(), json(notification))
                .then(repository.logDelivery(notification.id(), "realtime", "sent", null, null))
                .onErrorResume(error -> repository.logDelivery(notification.id(), "realtime", "failed",
                        error.getMessage(), null));
    }

    private Mono<Void> push(NotificationDTO notification, DeliveryProfile profile, NotificationEvent event) {
        if (!profile.pushEnabled() || profile.muted(event.type())) {
            return Mono.empty();
        }
        return repository.activeDevices(notification.userId()).collectList().flatMap(devices -> {
            if (devices.isEmpty()) {
                return Mono.empty();
            }
            return credentials.current().flatMap(optional -> optional
                    .map(creds -> sendAll(notification, event, devices, creds))
                    .orElseGet(() -> repository.logDelivery(notification.id(), "fcm", "skipped",
                            "FCM integration is not enabled", null)));
        });
    }

    private Mono<Void> sendAll(NotificationDTO notification, NotificationEvent event, Iterable<DeviceTarget> devices,
                               FcmCredentials creds) {
        PushMessage message = new PushMessage(notification.title(), notification.body(), pushData(notification, event),
                event.priority() == NotificationPriority.high);
        return Flux.fromIterable(devices).concatMap(device -> push.send(creds, device.token(), message)
                .flatMap(result -> record(notification, device, result))).then();
    }

    private Mono<Void> record(NotificationDTO notification, DeviceTarget device, PushResult result) {
        Mono<Void> log = repository.logDelivery(notification.id(), "fcm",
                result.status() == PushResult.Status.SENT ? "sent" : "failed", result.detail(), device.id());
        return result.status() == PushResult.Status.UNREGISTERED
                ? log.then(repository.deactivateDevice(device.token()))
                : log;
    }

    private Map<String, String> pushData(NotificationDTO notification, NotificationEvent event) {
        Map<String, String> data = new HashMap<>();
        if (event.data() != null) {
            data.putAll(event.data());
        }
        data.put("notificationId", notification.id().toString());
        data.put("type", notification.type());
        return data;
    }

    private String priority(NotificationEvent event) {
        return event.priority() == null ? "normal" : event.priority().name();
    }

    private OffsetDateTime expiry(NotificationEvent event) {
        return event.ttlSeconds() == null || event.ttlSeconds() <= 0 ? null
                : OffsetDateTime.now().plusSeconds(event.ttlSeconds());
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialise notification", e);
        }
    }

}
