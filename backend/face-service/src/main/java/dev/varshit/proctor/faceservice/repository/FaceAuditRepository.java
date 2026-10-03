package dev.varshit.proctor.faceservice.repository;

import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.faceservice.dto.FaceAuditDTO;
import dev.varshit.proctor.persistence.sql.Params;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Repository
public class FaceAuditRepository {

    public static final Set<String> EVENTS =
            Set.of("recognize", "live_recognize", "enroll", "replace", "remove", "unlock", "bulk_import");

    private static final String COLUMNS = "a.id, a.event,"
            + " case when can_view_user(a.actor_id) then a.actor_id end as actor_id,"
            + " case when can_view_user(a.actor_id) then user_name(a.actor_id) end as actor_name,"
            + " a.subject_id, user_name(a.subject_id) as subject_name,"
            + " a.matched, a.confidence, a.faces_detected, a.detail, a.created_at";

    private static final String FILTER = " from face_audit_log a"
            + " where (cast(:event as text) is null or a.event = cast(:event as face_event))"
            + " and (cast(:subject as uuid) is null or a.subject_id = cast(:subject as uuid))";

    private final SqlGateway gateway;

    public FaceAuditRepository(SqlGateway gateway) {
        this.gateway = gateway;
    }

    public Mono<Void> log(String event, UUID subjectId, Boolean matched, Float confidence, Integer faces, String detail) {
        return gateway.execute(
                        "select log_face_event(cast(:event as face_event), :subject, :matched, :confidence, :faces, :detail)",
                        Params.create()
                                .with("event", event)
                                .withNullable("subject", subjectId, UUID.class)
                                .withNullable("matched", matched, Boolean.class)
                                .withNullable("confidence", confidence, Float.class)
                                .withNullable("faces", faces, Integer.class)
                                .withNullable("detail", detail, String.class)
                                .build())
                .then();
    }

    public Mono<PageResponse<FaceAuditDTO>> search(String event, UUID subjectId, int page, int size) {
        var filter = Params.create()
                .withNullable("event", event, String.class)
                .withNullable("subject", subjectId, UUID.class);
        var countParams = Map.copyOf(filter.build());
        var pageParams = filter.with("limit", size).with("offset", (long) page * size).build();
        return gateway.queryLong("select count(*)" + FILTER, countParams)
                .flatMap(total -> gateway.queryMany(
                                "select " + COLUMNS + FILTER + " order by a.created_at desc limit :limit offset :offset",
                                pageParams, FaceAuditDTO.class)
                        .collectList()
                        .map(rows -> PageResponse.of(rows, total, page, size)));
    }
}
