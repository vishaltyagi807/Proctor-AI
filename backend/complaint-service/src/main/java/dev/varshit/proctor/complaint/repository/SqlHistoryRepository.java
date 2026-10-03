package dev.varshit.proctor.complaint.repository;

import dev.varshit.proctor.complaint.dto.HistoryDTO;
import dev.varshit.proctor.persistence.sql.Params;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
public class SqlHistoryRepository implements HistoryRepository {

    private final SqlGateway gateway;

    public SqlHistoryRepository(SqlGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public Flux<HistoryDTO> findByComplaint(UUID complaintId) {
        return gateway.queryMany(
                "select h.id, h.complaint_id, visible_actor(h.complaint_id, h.actor_id) as actor_id,"
                        + " user_name(visible_actor(h.complaint_id, h.actor_id)) as actor_name, h.from_status,"
                        + " h.to_status, h.note, h.created_at from complaint_history h"
                        + " where h.complaint_id = :id order by h.created_at, h.id",
                Params.create().with("id", complaintId).build(), HistoryDTO.class);
    }
}
