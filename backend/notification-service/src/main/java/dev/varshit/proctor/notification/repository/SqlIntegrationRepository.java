package dev.varshit.proctor.notification.repository;

import dev.varshit.proctor.notification.dto.IntegrationRow;
import dev.varshit.proctor.persistence.sql.Params;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Repository
public class SqlIntegrationRepository implements IntegrationRepository {

    private static final String COLUMNS = "provider, enabled, config, secret_ciphertext, updated_by, updated_at";

    private final SqlGateway gateway;

    public SqlIntegrationRepository(SqlGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public Flux<IntegrationRow> findAll() {
        return gateway.queryMany("select " + COLUMNS + " from integration_settings order by provider", Map.of(),
                IntegrationRow.class);
    }

    @Override
    public Mono<IntegrationRow> find(String provider) {
        return gateway.queryOne("select " + COLUMNS + " from integration_settings where provider = :provider",
                Params.create().with("provider", provider).build(), IntegrationRow.class);
    }

    @Override
    public Mono<Long> clear(String provider) {
        return gateway.execute("update integration_settings set enabled = false, secret_ciphertext = null,"
                + " config = '{}'::jsonb, updated_at = now() where provider = :provider",
                Params.create().with("provider", provider).build());
    }

    @Override
    public Mono<Long> upsert(String provider, boolean enabled, String configJson, String secretCiphertext,
                             UUID updatedBy) {
        return gateway.execute(
                "insert into integration_settings (provider, enabled, config, secret_ciphertext, updated_by)"
                        + " values (:provider, :enabled, cast(:config as jsonb), :secret, :by)"
                        + " on conflict (provider) do update set enabled = excluded.enabled, config = excluded.config,"
                        + " secret_ciphertext = coalesce(excluded.secret_ciphertext, integration_settings.secret_ciphertext),"
                        + " updated_by = excluded.updated_by, updated_at = now()",
                Params.create().with("provider", provider).with("enabled", enabled).with("config", configJson)
                        .withNullable("secret", secretCiphertext, String.class).with("by", updatedBy).build());
    }
}
