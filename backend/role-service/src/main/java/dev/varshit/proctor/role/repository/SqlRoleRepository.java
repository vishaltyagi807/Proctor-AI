package dev.varshit.proctor.role.repository;

import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.common.dto.PermissionDTO;
import dev.varshit.proctor.common.dto.RoleDTO;
import dev.varshit.proctor.common.dto.RoleWithPermissionDTO;
import dev.varshit.proctor.common.enums.EntityType;
import dev.varshit.proctor.common.enums.PermissionAction;
import dev.varshit.proctor.common.enums.PermissionScope;
import dev.varshit.proctor.customfields.CustomFieldSql;
import dev.varshit.proctor.persistence.search.FieldSpec;
import dev.varshit.proctor.persistence.search.PageParams;
import dev.varshit.proctor.persistence.search.PagedQuery;
import dev.varshit.proctor.persistence.search.PagedQueryRunner;
import dev.varshit.proctor.persistence.sql.Params;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import dev.varshit.proctor.role.dto.PatchRoleRequest;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Repository
public class SqlRoleRepository implements RoleRepository {

    private static final String ROLE_COLUMNS =
            "r.id, r.name, r.description, r.level, r.is_system as system, r.is_superuser as superuser, r.reveal_identity, r.created_at, "
            + CustomFieldSql.valuesColumn("roles", "r.id");

    private static final String PERMISSION_COLUMNS = "p.id, p.entity, p.action, p.scope, p.description";

    private static final String ROLE_WITH_PERMISSIONS = "select " + ROLE_COLUMNS + ","
            + " coalesce((select jsonb_agg(jsonb_build_object('id', p.id, 'entity', p.entity, 'action', p.action,"
            + " 'scope', p.scope, 'description', p.description) order by p.entity, p.action, p.scope)"
            + " from role_permission rp join permissions p on p.id = rp.permission_id where rp.role_id = r.id),"
            + " '[]'::jsonb) as permissions from roles r";

    private static final PagedQuery ROLE_QUERY = new PagedQuery(
            ROLE_COLUMNS,
            "roles r",
            Map.of(
                    "id", FieldSpec.uuid("r.id"),
                    "name", FieldSpec.text("r.name"),
                    "description", FieldSpec.text("r.description"),
                    "level", FieldSpec.integer("r.level"),
                    "system", FieldSpec.bool("r.is_system"),
                    "superuser", FieldSpec.bool("r.is_superuser"),
                    "revealIdentity", FieldSpec.bool("r.reveal_identity"),
                    "createdAt", FieldSpec.timestamp("r.created_at")
            ),
            "r.created_at",
            "r.id",
            CustomFieldSql.valuesExpression("roles", "r.id")
    );

    private static final PagedQuery PERMISSION_QUERY = new PagedQuery(
            PERMISSION_COLUMNS,
            "permissions p",
            Map.of(
                    "id", FieldSpec.uuid("p.id"),
                    "entity", FieldSpec.enumOf("p.entity", "entity", EntityType.class),
                    "action", FieldSpec.enumOf("p.action", "permission_action", PermissionAction.class),
                    "scope", FieldSpec.enumOf("p.scope", "permission_scope", PermissionScope.class),
                    "description", FieldSpec.text("p.description"),
                    "createdAt", FieldSpec.timestamp("p.created_at")
            ),
            "p.entity",
            "p.id"
    );

    private final SqlGateway gateway;
    private final PagedQueryRunner runner;

    public SqlRoleRepository(SqlGateway gateway, PagedQueryRunner runner) {
        this.gateway = gateway;
        this.runner = runner;
    }

    @Override
    public Mono<PageResponse<RoleDTO>> searchRoles(Map<String, String> filters, PageParams page) {
        return runner.run(ROLE_QUERY, filters, page, RoleDTO.class);
    }

    @Override
    public Mono<PageResponse<PermissionDTO>> searchPermissions(Map<String, String> filters, PageParams page) {
        return runner.run(PERMISSION_QUERY, filters, page, PermissionDTO.class);
    }

    @Override
    public Mono<RoleWithPermissionDTO> findWithPermissions(UUID id) {
        return gateway.queryOne(ROLE_WITH_PERMISSIONS + " where r.id = :id",
                Params.create().with("id", id).build(), RoleWithPermissionDTO.class);
    }

    @Override
    public Mono<RoleDTO> findById(UUID id) {
        return gateway.queryOne("select " + ROLE_COLUMNS + " from roles r where r.id = :id",
                Params.create().with("id", id).build(), RoleDTO.class);
    }

    @Override
    public Flux<PermissionDTO> findPermissions(UUID roleId) {
        return gateway.queryMany(
                "select " + PERMISSION_COLUMNS + " from role_permission rp join permissions p on p.id = rp.permission_id"
                        + " where rp.role_id = :id order by p.entity, p.action, p.scope",
                Params.create().with("id", roleId).build(), PermissionDTO.class);
    }

    @Override
    public Mono<Long> insert(UUID id, String name, String description, int level, boolean revealIdentity) {
        return gateway.execute(
                "insert into roles (id, name, description, level, reveal_identity)"
                        + " values (:id, :name, :description, :level, :reveal)",
                Params.create().with("id", id).with("name", name)
                        .withNullable("description", description, String.class).with("level", level)
                        .with("reveal", revealIdentity).build());
    }

    @Override
    public Mono<Long> update(UUID id, String name, String description, int level, boolean revealIdentity) {
        return gateway.execute(
                "update roles set name = :name, description = :description, level = :level,"
                        + " reveal_identity = :reveal where id = :id",
                Params.create().with("id", id).with("name", name)
                        .withNullable("description", description, String.class).with("level", level)
                        .with("reveal", revealIdentity).build());
    }

    @Override
    public Mono<Long> patch(UUID id, PatchRoleRequest request) {
        return gateway.execute(
                "update roles set name = coalesce(:name, name), description = coalesce(:description, description),"
                        + " level = coalesce(:level, level), reveal_identity = coalesce(:reveal, reveal_identity) where id = :id",
                Params.create().with("id", id)
                        .withNullable("name", request.name(), String.class)
                        .withNullable("description", request.description(), String.class)
                        .withNullable("level", request.level(), Integer.class)
                        .withNullable("reveal", request.revealIdentity(), Boolean.class).build());
    }

    @Override
    public Mono<Long> delete(UUID id) {
        return gateway.execute("delete from roles where id = :id", Params.create().with("id", id).build());
    }

    @Override
    public Mono<Long> addPermissions(UUID roleId, Set<UUID> permissionIds) {
        if (permissionIds.isEmpty()) {
            return Mono.just(0L);
        }
        return gateway.execute(
                "insert into role_permission (role_id, permission_id) select :role, unnest(:permissions) on conflict do nothing",
                Params.create().with("role", roleId).with("permissions", permissionIds.toArray(UUID[]::new)).build());
    }

    @Override
    public Mono<Long> removePermissions(UUID roleId, Set<UUID> permissionIds) {
        return gateway.execute(
                "delete from role_permission where role_id = :role and permission_id = any(:permissions)",
                Params.create().with("role", roleId).with("permissions", permissionIds.toArray(UUID[]::new)).build());
    }

    @Override
    public Mono<Long> removeAllPermissions(UUID roleId) {
        return gateway.execute("delete from role_permission where role_id = :role",
                Params.create().with("role", roleId).build());
    }
}
