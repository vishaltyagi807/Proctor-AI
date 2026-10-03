package dev.varshit.proctor.notification.repository;

import dev.varshit.proctor.notification.dto.DeviceDTO;
import dev.varshit.proctor.notification.dto.DeviceTarget;
import dev.varshit.proctor.notification.dto.PreferencesDTO;
import dev.varshit.proctor.persistence.sql.Params;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class SqlDeviceRepository implements DeviceRepository {

    private static final String COLUMNS = "d.id, d.platform, d.device_name, d.active, d.last_seen_at, d.created_at";

    private final SqlGateway gateway;

    public SqlDeviceRepository(SqlGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public Mono<UUID> register(String platform, String token, String name) {
        return gateway.queryOne("select register_device(:platform, :token, :name) as value",
                        Params.create().with("platform", platform).with("token", token)
                                .withNullable("name", name, String.class).build(), IdRow.class)
                .map(IdRow::value);
    }

    @Override
    public Mono<DeviceDTO> findById(UUID id) {
        return gateway.queryOne("select " + COLUMNS + " from notification_devices d where d.id = :id",
                Params.create().with("id", id).build(), DeviceDTO.class);
    }

    @Override
    public Flux<DeviceDTO> findMine() {
        return gateway.queryMany("select " + COLUMNS + " from notification_devices d where d.user_id = uid()"
                + " order by d.created_at desc", Map.of(), DeviceDTO.class);
    }

    @Override
    public Flux<DeviceTarget> findMyTargets() {
        return gateway.queryMany("select d.id, d.platform, d.token from notification_devices d"
                + " where d.user_id = uid() and d.active", Map.of(), DeviceTarget.class);
    }

    @Override
    public Mono<Long> delete(UUID id) {
        return gateway.execute("delete from notification_devices where id = :id", Params.create().with("id", id).build());
    }

    @Override
    public Mono<PreferencesDTO> preferences(UUID userId) {
        return gateway.queryOne("select push_enabled, realtime_enabled, muted_types from system_delivery_profile(:user)",
                Params.create().with("user", userId).build(), PreferencesDTO.class);
    }

    @Override
    public Mono<Long> savePreferences(UUID userId, PreferencesDTO preferences) {
        List<String> muted = preferences.mutedTypes() == null ? List.of() : preferences.mutedTypes();
        return gateway.execute(
                "insert into notification_preferences (user_id, push_enabled, realtime_enabled, muted_types)"
                        + " values (:user, :push, :realtime, :muted) on conflict (user_id) do update set"
                        + " push_enabled = excluded.push_enabled, realtime_enabled = excluded.realtime_enabled,"
                        + " muted_types = excluded.muted_types, updated_at = now()",
                Params.create().with("user", userId).with("push", preferences.pushEnabled())
                        .with("realtime", preferences.realtimeEnabled())
                        .with("muted", muted.toArray(String[]::new)).build());
    }

    private record IdRow(UUID value) {
    }
}
