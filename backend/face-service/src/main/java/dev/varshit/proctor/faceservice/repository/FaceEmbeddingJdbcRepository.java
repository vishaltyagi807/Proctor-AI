package dev.varshit.proctor.faceservice.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FaceEmbeddingJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public FaceEmbeddingJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Integer> findSelfEnrollCount(UUID userId) {
        List<Integer> rows = jdbcTemplate.query(
                "select coalesce((metadata ->> 'selfEnrollCount')::int, 0) as c from face_embeddings where id = ?",
                (rs, rowNum) -> rs.getInt("c"),
                userId);
        return rows.stream().findFirst();
    }

    public int resetSelfEnrollCount(UUID userId) {
        return jdbcTemplate.update(
                "update face_embeddings set metadata = (metadata::jsonb || jsonb_build_object('selfEnrollCount', 0))::json"
                        + " where id = ?",
                userId);
    }
}
