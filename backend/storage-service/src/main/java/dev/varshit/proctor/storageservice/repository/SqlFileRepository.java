package dev.varshit.proctor.storageservice.repository;

import dev.varshit.proctor.persistence.sql.Params;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import dev.varshit.proctor.storageservice.dto.FileDTO;
import dev.varshit.proctor.storageservice.dto.FileRef;
import dev.varshit.proctor.storageservice.dto.FileUsage;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@Repository
public class SqlFileRepository implements FileRepository {

    private static final String COLUMNS = "f.id, f.complaint_id,"
            + " visible_actor(f.complaint_id, f.uploaded_by) as uploaded_by,"
            + " user_name(visible_actor(f.complaint_id, f.uploaded_by)) as uploaded_by_name,"
            + " f.file_name, f.content_type, f.size_bytes, f.kind, f.status, f.created_at";

    private final SqlGateway gateway;

    public SqlFileRepository(SqlGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public Mono<Boolean> complaintVisible(UUID complaintId) {
        return gateway.queryLong("select count(*) from complaints where id = :id",
                Params.create().with("id", complaintId).build()).map(count -> count > 0);
    }

    @Override
    public Mono<FileUsage> usage(UUID complaintId) {
        return gateway.queryOne("select file_count, total_bytes from complaint_file_usage(:id)",
                Params.create().with("id", complaintId).build(), FileUsage.class);
    }

    @Override
    public Flux<FileDTO> findByComplaint(UUID complaintId) {
        return gateway.queryMany(
                "select " + COLUMNS + " from complaint_files f where f.complaint_id = :id"
                        + " and (f.status = 'uploaded' or f.uploaded_by = uid()) order by f.created_at, f.id",
                Params.create().with("id", complaintId).build(), FileDTO.class);
    }

    @Override
    public Mono<FileDTO> findById(UUID id) {
        return gateway.queryOne("select " + COLUMNS + " from complaint_files f where f.id = :id",
                Params.create().with("id", id).build(), FileDTO.class);
    }

    @Override
    public Mono<FileRef> findRef(UUID complaintId, UUID id) {
        return gateway.queryOne(
                "select f.id, f.complaint_id, f.file_name, f.content_type, f.size_bytes, f.file_url, f.status"
                        + " from complaint_files f where f.id = :id and f.complaint_id = :complaint"
                        + " and (f.status = 'uploaded' or f.uploaded_by = uid())",
                Params.create().with("id", id).with("complaint", complaintId).build(), FileRef.class);
    }

    @Override
    public Mono<Long> insertPending(UUID id, UUID complaintId, String fileName, String contentType, long sizeBytes,
                                    String key) {
        return gateway.execute(
                "insert into complaint_files (id, complaint_id, file_name, content_type, size_bytes, file_url)"
                        + " values (:id, :complaint, :name, :type, :size, :url)",
                Params.create().with("id", id).with("complaint", complaintId).with("name", fileName)
                        .with("type", contentType).with("size", sizeBytes).with("url", key).build());
    }

    @Override
    public Mono<Long> markUploaded(UUID id) {
        return gateway.execute("update complaint_files set status = 'uploaded' where id = :id and status = 'pending'",
                Params.create().with("id", id).build());
    }

    @Override
    public Mono<Long> delete(UUID id) {
        return gateway.execute("delete from complaint_files where id = :id", Params.create().with("id", id).build());
    }

    @Override
    public Flux<String> purgeStale(Duration olderThan) {
        return gateway.queryMany(
                "select purge_stale_uploads(cast(:age as interval)) as value",
                Params.create().with("age", olderThan.toSeconds() + " seconds").build(), KeyRow.class)
                .map(KeyRow::value);
    }

    private record KeyRow(String value) {
    }
}
