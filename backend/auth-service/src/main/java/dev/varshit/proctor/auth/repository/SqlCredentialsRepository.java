package dev.varshit.proctor.auth.repository;

import dev.varshit.proctor.auth.dto.Credentials;
import dev.varshit.proctor.persistence.sql.Params;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class SqlCredentialsRepository implements CredentialsRepository {

    private final SqlGateway gateway;

    public SqlCredentialsRepository(SqlGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public Mono<Credentials> findById(java.util.UUID id) {
        return gateway.queryOne(
                "select id, email, name, password, enabled, verified, created_at, updated_at from users where id = :id",
                Params.create().with("id", id).build(),
                Credentials.class);
    }

    @Override
    public Mono<Credentials> findByEmail(String email) {
        return gateway.queryOne(
                "select id, email, name, password, enabled, verified, created_at, updated_at"
                        + " from users where lower(email) = lower(:email)",
                Params.create().with("email", email).build(),
                Credentials.class);
    }
}
