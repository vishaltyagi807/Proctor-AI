package dev.varshit.proctor.department.repository;

import dev.varshit.proctor.common.dto.DepartmentDTO;
import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.department.dto.MemberDTO;
import dev.varshit.proctor.department.dto.PatchDepartmentRequest;
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

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Repository
public class SqlDepartmentRepository implements DepartmentRepository {

    private static final String COLUMNS =
            "d.id, d.name, d.code, d.description, d.active, d.created_at, d.updated_at, "
            + CustomFieldSql.valuesColumn("departments", "d.id");

    private static final PagedQuery QUERY = new PagedQuery(
            COLUMNS,
            "departments d",
            Map.of(
                    "id", FieldSpec.uuid("d.id"),
                    "name", FieldSpec.text("d.name"),
                    "code", FieldSpec.text("d.code"),
                    "description", FieldSpec.text("d.description"),
                    "active", FieldSpec.bool("d.active"),
                    "createdAt", FieldSpec.timestamp("d.created_at"),
                    "updatedAt", FieldSpec.timestamp("d.updated_at")
            ),
            "d.created_at",
            "d.id",
            CustomFieldSql.valuesExpression("departments", "d.id")
    );

    private final SqlGateway gateway;
    private final PagedQueryRunner runner;

    public SqlDepartmentRepository(SqlGateway gateway, PagedQueryRunner runner) {
        this.gateway = gateway;
        this.runner = runner;
    }

    @Override
    public Mono<PageResponse<DepartmentDTO>> search(Map<String, String> filters, PageParams page) {
        return runner.run(QUERY, filters, page, DepartmentDTO.class);
    }

    @Override
    public Mono<DepartmentDTO> findById(UUID id) {
        return gateway.queryOne(
                "select " + COLUMNS + " from departments d where d.id = :id",
                Params.create().with("id", id).build(),
                DepartmentDTO.class);
    }

    @Override
    public Mono<Long> insert(UUID id, String name, String code, String description, boolean active) {
        return gateway.execute(
                "insert into departments (id, name, code, description, active) values (:id, :name, :code, :description, :active)",
                Params.create().with("id", id).with("name", name).with("code", code)
                        .withNullable("description", description, String.class).with("active", active).build());
    }

    @Override
    public Mono<Long> update(UUID id, String name, String code, String description, boolean active) {
        return gateway.execute(
                "update departments set name = :name, code = :code, description = :description, active = :active where id = :id",
                Params.create().with("id", id).with("name", name).with("code", code)
                        .withNullable("description", description, String.class).with("active", active).build());
    }

    @Override
    public Mono<Long> patch(UUID id, PatchDepartmentRequest request) {
        return gateway.execute(
                "update departments set name = coalesce(:name, name), code = coalesce(:code, code),"
                        + " description = coalesce(:description, description), active = coalesce(:active, active) where id = :id",
                Params.create().with("id", id)
                        .withNullable("name", request.name(), String.class)
                        .withNullable("code", request.code(), String.class)
                        .withNullable("description", request.description(), String.class)
                        .withNullable("active", request.active(), Boolean.class).build());
    }

    @Override
    public Mono<Long> delete(UUID id) {
        return gateway.execute("delete from departments where id = :id", Params.create().with("id", id).build());
    }

    @Override
    public Flux<MemberDTO> findMembers(UUID departmentId) {
        return gateway.queryMany(
                "select u.id, u.email, u.name from department_users du join users u on u.id = du.user_id"
                        + " where du.department_id = :id order by u.name, u.id",
                Params.create().with("id", departmentId).build(),
                MemberDTO.class);
    }

    @Override
    public Mono<Long> addMembers(UUID departmentId, Set<UUID> userIds) {
        return gateway.execute(
                "insert into department_users (user_id, department_id) select unnest(:users), :department on conflict do nothing",
                Params.create().with("users", userIds.toArray(UUID[]::new)).with("department", departmentId).build());
    }

    @Override
    public Mono<Long> removeMembers(UUID departmentId, Set<UUID> userIds) {
        return gateway.execute(
                "delete from department_users where department_id = :department and user_id = any(:users)",
                Params.create().with("users", userIds.toArray(UUID[]::new)).with("department", departmentId).build());
    }
}
