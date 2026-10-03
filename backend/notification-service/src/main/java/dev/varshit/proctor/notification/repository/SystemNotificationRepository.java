package dev.varshit.proctor.notification.repository;

import dev.varshit.proctor.notification.dto.DeliveryProfile;
import dev.varshit.proctor.notification.dto.DeviceTarget;
import dev.varshit.proctor.notification.dto.IntegrationRow;
import dev.varshit.proctor.notification.dto.NotificationDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface SystemNotificationRepository {

    Mono<NotificationDTO> create(UUID userId, String type, String title, String body, String dataJson,
                                 String priority, String source, OffsetDateTime expiresAt);

    Mono<DeliveryProfile> deliveryProfile(UUID userId);

    Flux<DeviceTarget> activeDevices(UUID userId);

    Mono<Void> deactivateDevice(String token);

    Mono<Void> logDelivery(UUID notificationId, String channel, String status, String detail, UUID deviceId);

    Mono<IntegrationRow> integration(String provider);

    Mono<Long> purgeOld(int days);
}
