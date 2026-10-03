package dev.varshit.proctor.complaint.service;

import dev.varshit.proctor.complaint.dto.CommentDTO;
import dev.varshit.proctor.complaint.dto.CommentRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface CommentService {

    Flux<CommentDTO> list(UUID complaintId);

    Mono<CommentDTO> add(UUID complaintId, CommentRequest request);

    Mono<Void> delete(UUID complaintId, UUID commentId);
}
