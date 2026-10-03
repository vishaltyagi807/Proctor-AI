package dev.varshit.proctor.complaint.repository;

import dev.varshit.proctor.complaint.dto.CommentDTO;
import dev.varshit.proctor.persistence.sql.Params;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public class SqlCommentRepository implements CommentRepository {

    private static final String COLUMNS = "c.id, c.complaint_id, visible_actor(c.complaint_id, c.author_id) as author_id,"
            + " user_name(visible_actor(c.complaint_id, c.author_id)) as author_name,"
            + " c.body, c.internal, c.created_at";

    private final SqlGateway gateway;

    public SqlCommentRepository(SqlGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public Flux<CommentDTO> findByComplaint(UUID complaintId) {
        return gateway.queryMany(
                "select " + COLUMNS + " from complaint_comments c where c.complaint_id = :id order by c.created_at, c.id",
                Params.create().with("id", complaintId).build(), CommentDTO.class);
    }

    @Override
    public Mono<CommentDTO> findById(UUID id) {
        return gateway.queryOne("select " + COLUMNS + " from complaint_comments c where c.id = :id",
                Params.create().with("id", id).build(), CommentDTO.class);
    }

    @Override
    public Mono<Long> insert(UUID id, UUID complaintId, String body, boolean internal) {
        return gateway.execute(
                "insert into complaint_comments (id, complaint_id, body, internal) values (:id, :complaint, :body, :internal)",
                Params.create().with("id", id).with("complaint", complaintId).with("body", body)
                        .with("internal", internal).build());
    }

    @Override
    public Mono<Long> delete(UUID complaintId, UUID id) {
        return gateway.execute("delete from complaint_comments where id = :id and complaint_id = :complaint",
                Params.create().with("id", id).with("complaint", complaintId).build());
    }
}
