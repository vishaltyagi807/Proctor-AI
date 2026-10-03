package dev.varshit.proctor.customfields;

import dev.varshit.proctor.persistence.sql.Params;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

public class SqlCustomFieldContextLoader implements CustomFieldContextLoader {

    private final SqlGateway gateway;

    public SqlCustomFieldContextLoader(SqlGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public Mono<CustomFieldContext> forUser(UUID userId) {
        return roles(userId).zipWith(departments(userId), CustomFieldContext::new);
    }

    @Override
    public Mono<CustomFieldContext> forComplaint(UUID complaintId) {
        return gateway.queryOne(
                        "select (complaint_row(:id) ->> 'department_id')::uuid as department_id,"
                                + " (complaint_row(:id) ->> 'raised_by')::uuid as raised_by",
                        Params.create().with("id", complaintId).build(), ComplaintRefs.class)
                .flatMap(refs -> (refs.raisedBy() == null ? Mono.just(Set.<UUID>of()) : roles(refs.raisedBy()))
                        .map(roleIds -> new CustomFieldContext(roleIds,
                                refs.departmentId() == null ? Set.of() : Set.of(refs.departmentId()))))
                .defaultIfEmpty(CustomFieldContext.empty());
    }

    private Mono<Set<UUID>> roles(UUID userId) {
        return gateway.queryMany("select unnest(user_role_ids(:id)) as value",
                        Params.create().with("id", userId).build(), UuidRow.class)
                .map(UuidRow::value).collect(java.util.stream.Collectors.<UUID>toSet());
    }

    private Mono<Set<UUID>> departments(UUID userId) {
        return gateway.queryMany("select unnest(user_department_ids(:id)) as value",
                        Params.create().with("id", userId).build(), UuidRow.class)
                .map(UuidRow::value).collect(java.util.stream.Collectors.<UUID>toSet());
    }

    private record UuidRow(UUID value) {
    }

    private record ComplaintRefs(UUID departmentId, UUID raisedBy) {
    }
}
