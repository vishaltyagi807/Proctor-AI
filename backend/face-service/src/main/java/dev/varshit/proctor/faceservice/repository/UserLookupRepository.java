package dev.varshit.proctor.faceservice.repository;

import dev.varshit.proctor.faceservice.dto.MatchedUserDTO;
import dev.varshit.proctor.persistence.sql.Params;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public class UserLookupRepository {

    private final SqlGateway gateway;

    public UserLookupRepository(SqlGateway gateway) {
        this.gateway = gateway;
    }

    public Mono<MatchedUserDTO> findEnabledById(UUID id) {
        return gateway.queryOne(
                "select id, name, email from users where id = :id and enabled = true",
                Params.create().with("id", id).build(),
                MatchedUserDTO.class);
    }

    public Mono<MatchedUserDTO> findEnabledByEmail(String email) {
        return gateway.queryOne(
                "select id, name, email from users where lower(email) = lower(:email) and enabled = true",
                Params.create().with("email", email).build(),
                MatchedUserDTO.class);
    }
}
