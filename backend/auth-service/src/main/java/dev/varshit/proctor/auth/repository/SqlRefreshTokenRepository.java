package dev.varshit.proctor.auth.repository;

import dev.varshit.proctor.auth.dto.ClientContext;
import dev.varshit.proctor.auth.dto.RefreshTokenRecord;
import dev.varshit.proctor.persistence.sql.Params;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public class SqlRefreshTokenRepository implements RefreshTokenRepository {

    private final SqlGateway gateway;

    public SqlRefreshTokenRepository(SqlGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public Mono<Long> insert(UUID userId, UUID familyId, String tokenHash, OffsetDateTime expiresAt,
                             ClientContext client) {
        return gateway.execute(
                "insert into refresh_tokens (user_id, family_id, token_hash, expires_at, user_agent, ip_address)"
                        + " values (:user, :family, :hash, :expires, :agent, :ip)",
                Params.create().with("user", userId).with("family", familyId).with("hash", tokenHash)
                        .with("expires", expiresAt)
                        .withNullable("agent", client.userAgent(), String.class)
                        .withNullable("ip", client.ipAddress(), String.class).build());
    }

    @Override
    public Mono<Boolean> claim(String tokenHash) {
        return gateway.execute(
                        "update refresh_tokens set revoked_at = now()"
                                + " where token_hash = :hash and revoked_at is null and expires_at > now()",
                        Params.create().with("hash", tokenHash).build())
                .map(rows -> rows > 0);
    }

    @Override
    public Mono<RefreshTokenRecord> findByHash(String tokenHash) {
        return gateway.queryOne(
                "select user_id, family_id, expires_at, revoked_at from refresh_tokens where token_hash = :hash",
                Params.create().with("hash", tokenHash).build(), RefreshTokenRecord.class);
    }

    @Override
    public Mono<Long> revokeByHash(String tokenHash) {
        return gateway.execute(
                "update refresh_tokens set revoked_at = now() where family_id ="
                        + " (select family_id from refresh_tokens where token_hash = :hash) and revoked_at is null",
                Params.create().with("hash", tokenHash).build());
    }

    @Override
    public Mono<Long> revokeFamily(UUID familyId) {
        return gateway.execute(
                "update refresh_tokens set revoked_at = now() where family_id = :family and revoked_at is null",
                Params.create().with("family", familyId).build());
    }

    @Override
    public Mono<Long> revokeAllForUser(UUID userId) {
        return gateway.execute(
                "update refresh_tokens set revoked_at = now() where user_id = :user and revoked_at is null",
                Params.create().with("user", userId).build());
    }

    @Override
    public Mono<Long> purgeExpired(int retentionDays) {
        return gateway.execute(
                "delete from refresh_tokens where expires_at < now() - make_interval(days => :days)",
                Params.create().with("days", retentionDays).build());
    }
}
