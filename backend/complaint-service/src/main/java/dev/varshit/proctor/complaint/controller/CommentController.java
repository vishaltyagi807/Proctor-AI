package dev.varshit.proctor.complaint.controller;

import dev.varshit.proctor.complaint.dto.CommentDTO;
import dev.varshit.proctor.complaint.dto.CommentRequest;
import dev.varshit.proctor.complaint.service.CommentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/complaints/{complaintId}/comments")
public class CommentController {

    private final CommentService service;

    public CommentController(CommentService service) {
        this.service = service;
    }

    @GetMapping
    public Flux<CommentDTO> list(@PathVariable UUID complaintId) {
        return service.list(complaintId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<CommentDTO> add(@PathVariable UUID complaintId, @Valid @RequestBody CommentRequest request) {
        return service.add(complaintId, request);
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable UUID complaintId, @PathVariable UUID commentId) {
        return service.delete(complaintId, commentId);
    }
}
