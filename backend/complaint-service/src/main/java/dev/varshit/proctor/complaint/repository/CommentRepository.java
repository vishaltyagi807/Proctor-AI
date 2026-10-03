package dev.varshit.proctor.complaint.repository;

import dev.varshit.proctor.complaint.dto.CommentDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface CommentRepository {

    Flux<CommentDTO> findByComplaint(UUID complaintId);

    Mono<CommentDTO> findById(UUID id);

    Mono<Long> insert(UUID id, UUID complaintId, String body, boolean internal);

    Mono<Long> delete(UUID complaintId, UUID id);
}
