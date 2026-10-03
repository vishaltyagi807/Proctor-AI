package dev.varshit.proctor.complaint.repository;

import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.common.enums.ComplaintPriority;
import dev.varshit.proctor.common.enums.ComplaintStatus;
import dev.varshit.proctor.complaint.dto.ComplaintDTO;
import dev.varshit.proctor.complaint.dto.CreateComplaintRequest;
import dev.varshit.proctor.complaint.dto.PatchComplaintRequest;
import dev.varshit.proctor.customfields.CustomFieldSql;
import dev.varshit.proctor.persistence.search.FieldSpec;
import dev.varshit.proctor.persistence.search.PageParams;
import dev.varshit.proctor.persistence.search.PagedQuery;
import dev.varshit.proctor.persistence.search.PagedQueryRunner;
import dev.varshit.proctor.persistence.sql.Params;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class SqlComplaintRepository implements ComplaintRepository {

    private static final String COLUMNS = "c.id, c.title, c.description, c.category, c.priority, c.status,"
            + " c.student_id, user_name(c.student_id) as student_name,"
            + " jsonb_build_array(jsonb_build_object('id', c.student_id, 'name', user_name(c.student_id)))"
            + " || coalesce((select jsonb_agg(jsonb_build_object('id', s.user_id, 'name', user_name(s.user_id))"
            + " order by user_name(s.user_id)) from complaint_subjects s where s.complaint_id = c.id), '[]'::jsonb)"
            + " as subjects, c.assigned_to,"
            + " user_name(c.assigned_to) as assigned_to_name, c.department_id,"
            + " department_name(c.department_id) as department_name, c.resolution, c.resolved_at,"
            + " case when v.vis then c.raised_by end as raised_by,"
            + " case when v.vis then user_name(c.raised_by) end as raised_by_name,"
            + " case when v.vis then user_department_names(c.raised_by) end as raised_by_departments,"
            + " v.vis as reporter_visible, c.reveal_reporter, c.created_at, c.updated_at, "
            + CustomFieldSql.valuesColumn("complaints", "c.id");

    private static final String FROM = "complaints c cross join lateral (select can_see_reporter(c.id) as vis) v";

    private static final PagedQuery QUERY = new PagedQuery(
            COLUMNS,
            FROM,
            Map.ofEntries(
                    Map.entry("id", FieldSpec.uuid("c.id")),
                    Map.entry("title", FieldSpec.text("c.title")),
                    Map.entry("description", FieldSpec.text("c.description")),
                    Map.entry("category", FieldSpec.text("c.category")),
                    Map.entry("priority", FieldSpec.enumOf("c.priority", "complaint_priority", ComplaintPriority.class)),
                    Map.entry("status", FieldSpec.enumOf("c.status", "complaint_status", ComplaintStatus.class)),
                    Map.entry("studentId", FieldSpec.uuid("c.student_id")),
                    Map.entry("assignedTo", FieldSpec.uuid("c.assigned_to")),
                    Map.entry("departmentId", FieldSpec.uuid("c.department_id")),
                    Map.entry("createdAt", FieldSpec.timestamp("c.created_at")),
                    Map.entry("updatedAt", FieldSpec.timestamp("c.updated_at")),
                    Map.entry("resolvedAt", FieldSpec.timestamp("c.resolved_at"))
            ),
            "c.created_at",
            "c.id",
            CustomFieldSql.valuesExpression("complaints", "c.id")
    );

    private final SqlGateway gateway;
    private final PagedQueryRunner runner;

    public SqlComplaintRepository(SqlGateway gateway, PagedQueryRunner runner) {
        this.gateway = gateway;
        this.runner = runner;
    }

    @Override
    public Mono<PageResponse<ComplaintDTO>> search(Map<String, String> filters, PageParams page) {
        return runner.run(QUERY, filters, page, ComplaintDTO.class);
    }

    @Override
    public Mono<ComplaintDTO> findById(UUID id) {
        return gateway.queryOne("select " + COLUMNS + " from " + FROM + " where c.id = :id",
                Params.create().with("id", id).build(), ComplaintDTO.class);
    }

    @Override
    public Mono<Long> insert(UUID id, UUID studentId, CreateComplaintRequest request) {
        return gateway.execute(
                "insert into complaints (id, title, description, category, priority, student_id, department_id)"
                        + " values (:id, :title, :description, :category, cast(:priority as complaint_priority),"
                        + " :student, :department)",
                Params.create().with("id", id).with("title", request.title().trim())
                        .with("description", request.description().trim())
                        .with("category", request.categoryOrDefault())
                        .with("priority", request.priorityOrDefault().name())
                        .with("student", studentId)
                        .withNullable("department", request.departmentId(), UUID.class).build());
    }

    @Override
    public Mono<Long> insertSubjects(UUID id, List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Mono.just(0L);
        }
        return gateway.execute(
                "insert into complaint_subjects (complaint_id, user_id) select :id, unnest(:users) on conflict do nothing",
                Params.create().with("id", id).with("users", userIds.toArray(UUID[]::new)).build());
    }

    @Override
    public Mono<Long> patch(UUID id, PatchComplaintRequest request) {
        return gateway.execute(
                "update complaints set title = coalesce(:title, title), description = coalesce(:description, description),"
                        + " category = coalesce(:category, category),"
                        + " priority = coalesce(cast(:priority as complaint_priority), priority),"
                        + " status = coalesce(cast(:status as complaint_status), status),"
                        + " assigned_to = coalesce(:assignedTo, assigned_to),"
                        + " resolution = coalesce(:resolution, resolution),"
                        + " reveal_reporter = coalesce(:reveal, reveal_reporter) where id = :id",
                Params.create().with("id", id)
                        .withNullable("title", request.title(), String.class)
                        .withNullable("description", request.description(), String.class)
                        .withNullable("category", request.category(), String.class)
                        .withNullable("priority", request.priority() == null ? null : request.priority().name(), String.class)
                        .withNullable("status", request.status() == null ? null : request.status().name(), String.class)
                        .withNullable("assignedTo", request.assignedTo(), UUID.class)
                        .withNullable("resolution", request.resolution(), String.class)
                        .withNullable("reveal", request.revealReporter(), Boolean.class).build());
    }

    @Override
    public Mono<Long> setStatusNote(String note) {
        return gateway.execute("select set_config('app.status_note', :note, true)",
                Params.create().with("note", note == null ? "" : note).build());
    }

    @Override
    public Mono<dev.varshit.proctor.complaint.dto.ComplaintParties> parties(UUID id) {
        return gateway.queryOne(
                "select student_id, raised_by, assigned_to, department_id, title, subject_ids from complaint_parties(:id)",
                Params.create().with("id", id).build(), dev.varshit.proctor.complaint.dto.ComplaintParties.class);
    }

    @Override
    public Mono<Long> delete(UUID id) {
        return gateway.execute("delete from complaints where id = :id", Params.create().with("id", id).build());
    }
}
