package dev.varshit.proctor.faceservice.repository;

import dev.varshit.proctor.faceservice.dto.EnrollmentDTO;
import dev.varshit.proctor.persistence.sql.Params;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public class FaceSearchRepository {

    public record FaceCandidate(UUID userId, boolean visible, boolean detailed, String name, String email, double score) {
    }

    private final SqlGateway gateway;

    public FaceSearchRepository(SqlGateway gateway) {
        this.gateway = gateway;
    }

    public Flux<EnrollmentDTO> listVisible(int selfEnrollLimit) {
        return gateway.queryMany(
                "select f.id as user_id,"
                        + " coalesce(user_name(f.id), f.metadata ->> 'name') as name,"
                        + " f.metadata ->> 'email' as email,"
                        + " f.metadata ->> 'enrolledAt' as enrolled_at,"
                        + " coalesce((f.metadata ->> 'selfEnrollCount')::int, 0) as self_enroll_count,"
                        + " coalesce((f.metadata ->> 'selfEnrollCount')::int, 0) >= :limit as self_enroll_locked,"
                        + " can_manage_face('update'::permission_action, f.id) as can_replace,"
                        + " can_manage_face('delete'::permission_action, f.id) as can_remove,"
                        + " can_unlock_face(f.id) as can_unlock"
                        + " from face_embeddings f"
                        + " where can_view_face_enrollment(f.id)"
                        + " order by f.metadata ->> 'enrolledAt' desc",
                Params.create().with("limit", selfEnrollLimit).build(),
                EnrollmentDTO.class);
    }

    public Mono<FaceCandidate> findBestMatch(float[] embedding, boolean live) {
        return gateway.queryOne(
                "select n.id as user_id,"
                        + " can_see_face(n.id, :live) as visible,"
                        + " can_see_face_details(n.id) as detailed,"
                        + " coalesce(user_name(n.id), n.metadata ->> 'name') as name,"
                        + " n.metadata ->> 'email' as email,"
                        + " n.score"
                        + " from (select f.id, f.metadata, 1 - (f.embedding <=> cast(:vector as vector)) as score"
                        + " from face_embeddings f"
                        + " order by f.embedding <=> cast(:vector as vector)"
                        + " limit 1) n",
                Params.create().with("vector", toVectorLiteral(embedding)).with("live", live).build(),
                FaceCandidate.class);
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder builder = new StringBuilder(embedding.length * 12 + 2).append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(embedding[i]);
        }
        return builder.append(']').toString();
    }
}
