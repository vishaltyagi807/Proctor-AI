package dev.varshit.proctor.notification.repository;

import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.notification.dto.NotificationDTO;
import dev.varshit.proctor.persistence.search.FieldSpec;
import dev.varshit.proctor.persistence.search.PageParams;
import dev.varshit.proctor.persistence.search.PagedQuery;
import dev.varshit.proctor.persistence.search.PagedQueryRunner;
import dev.varshit.proctor.persistence.sql.Params;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Repository
public class SqlNotificationRepository implements NotificationRepository {

    private static final String COLUMNS =
            "n.id, n.user_id, n.type, n.title, n.body, n.data, n.priority, n.source, n.read_at, n.expires_at, n.created_at";

    private static final PagedQuery QUERY = new PagedQuery(
            COLUMNS,
            "notifications n",
            Map.of(
                    "id", FieldSpec.uuid("n.id"),
                    "type", FieldSpec.text("n.type"),
                    "title", FieldSpec.text("n.title"),
                    "priority", FieldSpec.text("n.priority"),
                    "source", FieldSpec.text("n.source"),
                    "readAt", FieldSpec.timestamp("n.read_at"),
                    "createdAt", FieldSpec.timestamp("n.created_at")
            ),
            "n.created_at",
            "n.id"
    );

    private final SqlGateway gateway;
    private final PagedQueryRunner runner;

    public SqlNotificationRepository(SqlGateway gateway, PagedQueryRunner runner) {
        this.gateway = gateway;
        this.runner = runner;
    }

    @Override
    public Mono<PageResponse<NotificationDTO>> search(Map<String, String> filters, PageParams page) {
        return runner.run(QUERY, filters, page, NotificationDTO.class);
    }

    @Override
    public Mono<Long> unreadCount() {
        return gateway.queryLong("select count(*) from notifications where read_at is null"
                + " and (expires_at is null or expires_at > now())", Map.of());
    }

    @Override
    public Mono<Long> markRead(UUID id) {
        return gateway.execute("update notifications set read_at = coalesce(read_at, now()) where id = :id",
                Params.create().with("id", id).build());
    }

    @Override
    public Mono<Long> markAllRead() {
        return gateway.execute("update notifications set read_at = now() where read_at is null", Map.of());
    }

    @Override
    public Mono<Long> delete(UUID id) {
        return gateway.execute("delete from notifications where id = :id", Params.create().with("id", id).build());
    }

    @Override
    public Mono<java.util.List<UUID>> resolveRecipients(java.util.Set<UUID> users, java.util.Set<UUID> roles,
                                                        java.util.Set<UUID> departments) {
        return gateway.queryMany(
                        "select unnest(resolve_notification_recipients(:users, :roles, :departments)) as value",
                        Params.create().with("users", users.toArray(UUID[]::new))
                                .with("roles", roles.toArray(UUID[]::new))
                                .with("departments", departments.toArray(UUID[]::new)).build(),
                        UuidRow.class)
                .map(UuidRow::value).collectList();
    }

    private record UuidRow(UUID value) {
    }

    @Override
    public Mono<Boolean> can(String entity, String action) {
        return gateway.queryLong("select case when can_access(cast(:entity as entity), cast(:action as permission_action))"
                                + " then 1 else 0 end",
                        Params.create().with("entity", entity).with("action", action).build())
                .map(value -> value == 1L);
    }
}
