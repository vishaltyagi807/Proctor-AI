package dev.varshit.proctor.user.repository;

import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.common.dto.UserDTO;
import dev.varshit.proctor.common.dto.UserInfoDTO;
import dev.varshit.proctor.customfields.CustomFieldSql;
import dev.varshit.proctor.persistence.search.FieldSpec;
import dev.varshit.proctor.persistence.search.PageParams;
import dev.varshit.proctor.persistence.search.PagedQuery;
import dev.varshit.proctor.persistence.search.PagedQueryRunner;
import dev.varshit.proctor.persistence.sql.Params;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Repository
public class SqlUserRepository implements UserRepository {

    private static final String USER_COLUMNS =
            "u.id, u.email, u.name, u.enabled, u.verified, u.created_at, u.updated_at, "
            + CustomFieldSql.valuesColumn("users", "u.id");

    private static final String INFO_COLUMNS = USER_COLUMNS + ","
            + " coalesce((select jsonb_agg(jsonb_build_object('id', r.id, 'name', r.name, 'description', r.description,"
            + " 'level', r.level, 'system', r.is_system, 'superuser', r.is_superuser, 'reveal_identity', r.reveal_identity, 'created_at', r.created_at)"
            + " order by r.level, r.name) from user_roles ur join roles r on r.id = ur.role_id"
            + " where ur.user_id = u.id), '[]'::jsonb) as roles,"
            + " coalesce((select jsonb_agg(jsonb_build_object('id', d.id, 'name', d.name, 'code', d.code,"
            + " 'description', d.description, 'active', d.active, 'created_at', d.created_at,"
            + " 'updated_at', d.updated_at) order by d.name) from department_users du"
            + " join departments d on d.id = du.department_id where du.user_id = u.id), '[]'::jsonb) as departments";

    private static final PagedQuery QUERY = new PagedQuery(
            INFO_COLUMNS,
            "users u",
            Map.of(
                    "id", FieldSpec.uuid("u.id"),
                    "email", FieldSpec.text("u.email"),
                    "name", FieldSpec.text("u.name"),
                    "enabled", FieldSpec.bool("u.enabled"),
                    "verified", FieldSpec.bool("u.verified"),
                    "createdAt", FieldSpec.timestamp("u.created_at"),
                    "updatedAt", FieldSpec.timestamp("u.updated_at"),
                    "departmentId", FieldSpec.uuidMember("department_users", "user_id", "u.id", "department_id"),
                    "roleId", FieldSpec.uuidMember("user_roles", "user_id", "u.id", "role_id")
            ),
            "u.created_at",
            "u.id",
            CustomFieldSql.valuesExpression("users", "u.id")
    );

    private final SqlGateway gateway;
    private final PagedQueryRunner runner;

    public SqlUserRepository(SqlGateway gateway, PagedQueryRunner runner) {
        this.gateway = gateway;
        this.runner = runner;
    }

    @Override
    public Mono<PageResponse<UserInfoDTO>> search(Map<String, String> filters, PageParams page) {
        return runner.run(QUERY, filters, page, UserInfoDTO.class);
    }

    @Override
    public Mono<UserInfoDTO> findInfoById(UUID id) {
        return gateway.queryOne("select " + INFO_COLUMNS + " from users u where u.id = :id",
                Params.create().with("id", id).build(), UserInfoDTO.class);
    }

    @Override
    public Mono<UserDTO> findById(UUID id) {
        return gateway.queryOne("select " + USER_COLUMNS + " from users u where u.id = :id",
                Params.create().with("id", id).build(), UserDTO.class);
    }

    @Override
    public Flux<String> findExistingEmails(Collection<String> emails) {
        return gateway.queryMany(
                "select email as value from users where lower(email) = any(:emails)",
                Params.create().with("emails", emails.stream().map(String::toLowerCase).toArray(String[]::new)).build(),
                EmailRow.class).map(EmailRow::value);
    }

    @Override
    public Mono<Long> insert(UUID id, String email, String name, String passwordHash, boolean enabled,
                             boolean verified) {
        return gateway.execute(
                "insert into users (id, email, name, password, enabled, verified)"
                        + " values (:id, :email, :name, :password, :enabled, :verified)",
                Params.create().with("id", id).with("email", email).with("name", name).with("password", passwordHash)
                        .with("enabled", enabled).with("verified", verified).build());
    }

    @Override
    public Mono<Long> update(UUID id, String email, String name, String passwordHash, boolean enabled,
                             boolean verified) {
        return gateway.execute(
                "update users set email = :email, name = :name, password = coalesce(:password, password),"
                        + " enabled = :enabled, verified = :verified where id = :id",
                Params.create().with("id", id).with("email", email).with("name", name)
                        .withNullable("password", passwordHash, String.class)
                        .with("enabled", enabled).with("verified", verified).build());
    }

    @Override
    public Mono<Long> patch(UUID id, String email, String name, String passwordHash, Boolean enabled,
                            Boolean verified) {
        return gateway.execute(
                "update users set email = coalesce(:email, email), name = coalesce(:name, name),"
                        + " password = coalesce(:password, password), enabled = coalesce(:enabled, enabled),"
                        + " verified = coalesce(:verified, verified) where id = :id",
                Params.create().with("id", id)
                        .withNullable("email", email, String.class)
                        .withNullable("name", name, String.class)
                        .withNullable("password", passwordHash, String.class)
                        .withNullable("enabled", enabled, Boolean.class)
                        .withNullable("verified", verified, Boolean.class).build());
    }

    @Override
    public Mono<Long> deleteAll(Set<UUID> ids) {
        return gateway.execute("delete from users where id = any(:ids)",
                Params.create().with("ids", ids.toArray(UUID[]::new)).build());
    }

    private record EmailRow(String value) {
    }
}
