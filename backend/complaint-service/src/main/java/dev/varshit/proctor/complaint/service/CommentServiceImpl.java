package dev.varshit.proctor.complaint.service;

import dev.varshit.proctor.common.exception.NotFoundException;
import dev.varshit.proctor.complaint.dto.CommentDTO;
import dev.varshit.proctor.complaint.dto.CommentRequest;
import dev.varshit.proctor.complaint.repository.CommentRepository;
import dev.varshit.proctor.complaint.repository.ComplaintRepository;
import dev.varshit.proctor.persistence.rls.SecureTransaction;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class CommentServiceImpl implements CommentService {

    private final CommentRepository comments;
    private final ComplaintRepository complaints;
    private final SecureTransaction transaction;
    private final ComplaintNotifier notifier;

    public CommentServiceImpl(CommentRepository comments, ComplaintRepository complaints,
                              SecureTransaction transaction, ComplaintNotifier notifier) {
        this.notifier = notifier;
        this.comments = comments;
        this.complaints = complaints;
        this.transaction = transaction;
    }

    @Override
    public Flux<CommentDTO> list(UUID complaintId) {
        return transaction.flux(() -> requireComplaint(complaintId).thenMany(comments.findByComplaint(complaintId)));
    }

    @Override
    public Mono<CommentDTO> add(UUID complaintId, CommentRequest request) {
        UUID id = UUID.randomUUID();
        return transaction.mono(() -> requireComplaint(complaintId)
                .then(comments.insert(id, complaintId, request.body().trim(), request.internal()))
                .then(comments.findById(id))
                .switchIfEmpty(Mono.error(new NotFoundException("Comment not found"))))
                .flatMap(comment -> notifier.commented(complaintId, request.internal()).thenReturn(comment));
    }

    @Override
    public Mono<Void> delete(UUID complaintId, UUID commentId) {
        return transaction.mono(() -> comments.delete(complaintId, commentId)
                .flatMap(rows -> rows == 0
                        ? Mono.<Void>error(new NotFoundException("Comment not found"))
                        : Mono.empty()));
    }

    private Mono<Void> requireComplaint(UUID id) {
        return complaints.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Complaint " + id + " not found")))
                .then();
    }
}
