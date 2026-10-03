package dev.varshit.proctor.complaint.repository;

import dev.varshit.proctor.complaint.dto.HistoryDTO;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface HistoryRepository {

    Flux<HistoryDTO> findByComplaint(UUID complaintId);
}
