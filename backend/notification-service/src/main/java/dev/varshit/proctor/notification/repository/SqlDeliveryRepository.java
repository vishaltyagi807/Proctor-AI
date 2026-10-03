package dev.varshit.proctor.notification.repository;

import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.notification.dto.DeliveryDTO;
import dev.varshit.proctor.notification.dto.DeliveryStatsDTO;
import dev.varshit.proctor.persistence.search.FieldSpec;
import dev.varshit.proctor.persistence.search.PageParams;
import dev.varshit.proctor.persistence.search.PagedQuery;
import dev.varshit.proctor.persistence.search.PagedQueryRunner;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.Map;

@Repository
public class SqlDeliveryRepository implements DeliveryRepository {

    private static final PagedQuery QUERY = new PagedQuery(
            "d.id, d.notification_id, d.channel, d.status, d.detail, d.device_id, d.created_at",
            "notification_deliveries d",
            Map.of(
                    "channel", FieldSpec.text("d.channel"),
                    "status", FieldSpec.text("d.status"),
                    "notificationId", FieldSpec.uuid("d.notification_id"),
                    "createdAt", FieldSpec.timestamp("d.created_at")
            ),
            "d.created_at",
            "d.id"
    );

    private final SqlGateway gateway;
    private final PagedQueryRunner runner;

    public SqlDeliveryRepository(SqlGateway gateway, PagedQueryRunner runner) {
        this.gateway = gateway;
        this.runner = runner;
    }

    @Override
    public Mono<PageResponse<DeliveryDTO>> search(Map<String, String> filters, PageParams page) {
        return runner.run(QUERY, filters, page, DeliveryDTO.class);
    }

    @Override
    public Mono<DeliveryStatsDTO> stats() {
        return gateway.queryOne(
                "select"
                        + " (select count(*) from notification_deliveries where channel = 'realtime' and status = 'sent'"
                        + " and created_at > now() - interval '24 hours') as realtime_sent,"
                        + " (select count(*) from notification_deliveries where channel = 'fcm' and status = 'sent'"
                        + " and created_at > now() - interval '24 hours') as fcm_sent,"
                        + " (select count(*) from notification_deliveries where channel = 'fcm' and status = 'failed'"
                        + " and created_at > now() - interval '24 hours') as fcm_failed,"
                        + " (select count(*) from notification_deliveries where status = 'skipped'"
                        + " and created_at > now() - interval '24 hours') as skipped,"
                        + " (select count(*) from notification_devices where active) as active_devices,"
                        + " (select count(*) from notifications where created_at > now() - interval '24 hours') as notifications_last24h",
                Map.of(), DeliveryStatsDTO.class);
    }
}
