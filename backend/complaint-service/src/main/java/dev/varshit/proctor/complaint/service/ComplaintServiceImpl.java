package dev.varshit.proctor.complaint.service;

import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.common.enums.CustomFieldEntity;
import dev.varshit.proctor.common.exception.ForbiddenException;
import dev.varshit.proctor.customfields.CustomFieldContextLoader;
import dev.varshit.proctor.customfields.CustomFieldService;
import dev.varshit.proctor.customfields.WriteMode;
import dev.varshit.proctor.common.exception.NotFoundException;
import dev.varshit.proctor.complaint.dto.AssignRequest;
import dev.varshit.proctor.complaint.dto.ComplaintDTO;
import dev.varshit.proctor.complaint.dto.CreateComplaintRequest;
import dev.varshit.proctor.complaint.dto.HistoryDTO;
import dev.varshit.proctor.complaint.dto.PatchComplaintRequest;
import dev.varshit.proctor.complaint.dto.StatusRequest;
import dev.varshit.proctor.complaint.repository.ComplaintRepository;
import dev.varshit.proctor.complaint.repository.HistoryRepository;
import dev.varshit.proctor.persistence.rls.SecureTransaction;
import dev.varshit.proctor.persistence.search.PageParams;
import dev.varshit.proctor.security.CurrentUser;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Service
public class ComplaintServiceImpl implements ComplaintService {

    private final ComplaintRepository complaints;
    private final HistoryRepository history;
    private final SecureTransaction transaction;
    private final CustomFieldService customFields;
    private final CustomFieldContextLoader contexts;
    private final ComplaintNotifier notifier;

    public ComplaintServiceImpl(ComplaintRepository complaints, HistoryRepository history,
                                SecureTransaction transaction, CustomFieldService customFields,
                                CustomFieldContextLoader contexts, ComplaintNotifier notifier) {
        this.complaints = complaints;
        this.history = history;
        this.transaction = transaction;
        this.customFields = customFields;
        this.contexts = contexts;
        this.notifier = notifier;
    }

    @Override
    public Mono<PageResponse<ComplaintDTO>> query(Map<String, String> filters, PageParams page) {
        return transaction.mono(() -> complaints.search(filters, page));
    }

    @Override
    public Mono<ComplaintDTO> get(UUID id) {
        return transaction.mono(() -> load(id));
    }

    @Override
    public Mono<ComplaintDTO> create(CreateComplaintRequest request) {
        return CurrentUser.require().flatMap(principal -> {
            UUID id = UUID.randomUUID();
            UUID student = request.studentId() == null ? principal.id() : request.studentId();
            return transaction.monoAs(principal, () -> complaints.insert(id, student, request)
                    .then(complaints.insertSubjects(id, request.otherSubjects(student)))
                    .then(applyCustomFields(id, request.customFields(), WriteMode.CREATE))
                    .then(load(id)))
                    .flatMap(dto -> notifier.created(dto.id()).thenReturn(dto));
        });
    }

    @Override
    public Mono<ComplaintDTO> patch(UUID id, PatchComplaintRequest request) {
        return transaction.mono(() -> complaints.setStatusNote(request.note())
                .then(complaints.patch(id, request))
                .flatMap(rows -> requireChanged(rows, id))
                .then(applyCustomFields(id, request.customFields(), WriteMode.MERGE))
                .then(load(id)))
                .flatMap(dto -> announce(dto, request));
    }

    @Override
    public Mono<ComplaintDTO> changeStatus(UUID id, StatusRequest request) {
        return patch(id, new PatchComplaintRequest(null, null, null, null, request.status(), null,
                request.resolution(), request.note(), null, null));
    }

    @Override
    public Mono<ComplaintDTO> assign(UUID id, AssignRequest request) {
        return patch(id, new PatchComplaintRequest(null, null, null, null, null, request.assignedTo(), null, null, null, null));
    }

    @Override
    public Mono<Void> delete(UUID id) {
        return transaction.mono(() -> complaints.delete(id).flatMap(rows -> requireChanged(rows, id)));
    }

    @Override
    public Flux<HistoryDTO> history(UUID id) {
        return transaction.flux(() -> load(id).thenMany(history.findByComplaint(id)));
    }

    private Mono<ComplaintDTO> announce(ComplaintDTO dto, PatchComplaintRequest request) {
        Mono<Void> status = request.status() == null ? Mono.empty() : notifier.statusChanged(dto.id(), dto.status());
        Mono<Void> assigned = request.assignedTo() == null ? Mono.empty() : notifier.assigned(dto.id());
        return status.then(assigned).thenReturn(dto);
    }

    private Mono<Void> applyCustomFields(UUID id, java.util.Map<String, Object> values, WriteMode mode) {
        return contexts.forComplaint(id).flatMap(context ->
                customFields.apply(CustomFieldEntity.complaints, id, values, mode, context));
    }

    private Mono<ComplaintDTO> load(UUID id) {
        return complaints.findById(id).switchIfEmpty(Mono.error(notFound(id)));
    }

    private Mono<Void> requireChanged(long rows, UUID id) {
        if (rows > 0) {
            return Mono.empty();
        }
        return complaints.findById(id)
                .flatMap(existing -> Mono.<Void>error(new ForbiddenException("You are not allowed to modify this complaint")))
                .switchIfEmpty(Mono.error(notFound(id)));
    }

    private NotFoundException notFound(UUID id) {
        return new NotFoundException("Complaint " + id + " not found");
    }
}
