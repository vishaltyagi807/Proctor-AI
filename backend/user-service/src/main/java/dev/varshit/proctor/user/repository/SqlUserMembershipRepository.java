package dev.varshit.proctor.user.repository;

import dev.varshit.proctor.common.dto.DepartmentDTO;
import dev.varshit.proctor.common.dto.PermissionDTO;
import dev.varshit.proctor.common.dto.RoleWithPermissionDTO;
import dev.varshit.proctor.persistence.sql.Params;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

@Repository
public class SqlUserMembershipRepository implements UserMembershipRepository {

    private final SqlGateway gateway;

    public SqlUserMembershipRepository(SqlGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public Flux<RoleWithPermissionDTO> findRoles(UUID userId) {
        return gateway.queryMany(
                "select r.id, r.name, r.description, r.level, r.is_system as system, r.is_superuser as superuser, r.reveal_identity,"
                        + " r.created_at, coalesce((select jsonb_agg(jsonb_build_object('id', p.id, 'entity', p.entity,"
                        + " 'action', p.action, 'scope', p.scope, 'description', p.description)"
                        + " order by p.entity, p.action, p.scope) from role_permission rp"
                        + " join permissions p on p.id = rp.permission_id where rp.role_id = r.id), '[]'::jsonb) as permissions"
                        + " from user_roles ur join roles r on r.id = ur.role_id where ur.user_id = :id"
                        + " order by r.level, r.name",
                Params.create().with("id", userId).build(), RoleWithPermissionDTO.class);
    }

    @Override
    public Flux<PermissionDTO> findPermissions(UUID userId) {
        return gateway.queryMany(
                "select distinct p.id, p.entity, p.action, p.scope, p.description from user_roles ur"
                        + " join role_permission rp on rp.role_id = ur.role_id"
                        + " join permissions p on p.id = rp.permission_id where ur.user_id = :id"
                        + " order by p.entity, p.action, p.scope",
                Params.create().with("id", userId).build(), PermissionDTO.class);
    }

    @Override
    public Flux<DepartmentDTO> findDepartments(UUID userId) {
        return gateway.queryMany(
                "select d.id, d.name, d.code, d.description, d.active, d.created_at, d.updated_at"
                        + " from department_users du join departments d on d.id = du.department_id"
                        + " where du.user_id = :id order by d.name",
                Params.create().with("id", userId).build(), DepartmentDTO.class);
    }

    @Override
    public Mono<Long> addRoles(UUID userId, Set<UUID> roleIds) {
        if (roleIds.isEmpty()) {
            return Mono.just(0L);
        }
        return gateway.execute(
                "insert into user_roles (user_id, role_id) select :user, unnest(:roles) on conflict do nothing",
                Params.create().with("user", userId).with("roles", roleIds.toArray(UUID[]::new)).build());
    }

    @Override
    public Mono<Long> retainRoles(UUID userId, Set<UUID> roleIds) {
        return gateway.execute("delete from user_roles where user_id = :user and not (role_id = any(:roles))",
                Params.create().with("user", userId).with("roles", roleIds.toArray(UUID[]::new)).build());
    }

    @Override
    public Mono<Long> addDepartments(UUID userId, Set<UUID> departmentIds) {
        if (departmentIds.isEmpty()) {
            return Mono.just(0L);
        }
        return gateway.execute(
                "insert into department_users (user_id, department_id) select :user, unnest(:departments) on conflict do nothing",
                Params.create().with("user", userId).with("departments", departmentIds.toArray(UUID[]::new)).build());
    }

    @Override
    public Mono<Long> retainDepartments(UUID userId, Set<UUID> departmentIds) {
        return gateway.execute(
                "delete from department_users where user_id = :user and not (department_id = any(:departments))",
                Params.create().with("user", userId).with("departments", departmentIds.toArray(UUID[]::new)).build());
    }
}
