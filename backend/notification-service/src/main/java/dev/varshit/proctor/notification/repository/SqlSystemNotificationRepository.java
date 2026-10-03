package dev.varshit.proctor.notification.repository;

import dev.varshit.proctor.notification.dto.DeliveryProfile;
import dev.varshit.proctor.notification.dto.DeviceTarget;
import dev.varshit.proctor.notification.dto.IntegrationRow;
import dev.varshit.proctor.notification.dto.NotificationDTO;
import dev.varshit.proctor.persistence.sql.Params;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public class SqlSystemNotificationRepository implements SystemNotificationRepository {

    private final SqlGateway gateway;

    public SqlSystemNotificationRepository(SqlGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public Mono<NotificationDTO> create(UUID userId, String type, String title, String body, String dataJson,
                                        String priority, String source, OffsetDateTime expiresAt) {
        return gateway.queryOne(
                        "select * from system_create_notification(:user, :type, :title, :body, cast(:data as jsonb),"
                                + " :priority, :source, :expires)",
                        Params.create().with("user", userId).with("type", type).with("title", title)
                                .withNullable("body", body, String.class).with("data", dataJson)
                                .with("priority", priority).withNullable("source", source, String.class)
                                .withNullable("expires", expiresAt, OffsetDateTime.class).build(),
                        NotificationDTO.class)
                .filter(notification -> notification.id() != null);
    }

    @Override
    public Mono<DeliveryProfile> deliveryProfile(UUID userId) {
        return gateway.queryOne("select push_enabled, realtime_enabled, muted_types from system_delivery_profile(:user)",
                Params.create().with("user", userId).build(), DeliveryProfile.class);
    }

    @Override
    public Flux<DeviceTarget> activeDevices(UUID userId) {
        return gateway.queryMany("select id, platform, token from system_active_devices(:user)",
                Params.create().with("user", userId).build(), DeviceTarget.class);
    }

    @Override
    public Mono<Void> deactivateDevice(String token) {
        return gateway.execute("select system_deactivate_device(:token)", Params.create().with("token", token).build())
                .then();
    }

    @Override
    public Mono<Void> logDelivery(UUID notificationId, String channel, String status, String detail, UUID deviceId) {
        return gateway.execute("select system_log_delivery(:notification, :channel, :status, :detail, :device)",
                Params.create().withNullable("notification", notificationId, UUID.class).with("channel", channel)
                        .with("status", status).withNullable("detail", detail, String.class)
                        .withNullable("device", deviceId, UUID.class).build()).then();
    }

    @Override
    public Mono<IntegrationRow> integration(String provider) {
        return gateway.queryOne(
                "select :provider as provider, enabled, config, secret_ciphertext, null::uuid as updated_by,"
                        + " null::timestamptz as updated_at from system_integration(:provider)",
                Params.create().with("provider", provider).build(), IntegrationRow.class);
    }

    @Override
    public Mono<Long> purgeOld(int days) {
        return gateway.queryLong("select purge_old_notifications(:days)", Params.create().with("days", days).build());
    }
}
