package dev.varshit.proctor.department.service;

import dev.varshit.proctor.common.dto.DepartmentDTO;
import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.common.enums.CustomFieldEntity;
import dev.varshit.proctor.common.exception.ForbiddenException;
import dev.varshit.proctor.customfields.CustomFieldContext;
import dev.varshit.proctor.customfields.CustomFieldService;
import dev.varshit.proctor.customfields.WriteMode;
import dev.varshit.proctor.common.exception.NotFoundException;
import dev.varshit.proctor.department.dto.CreateDepartmentRequest;
import dev.varshit.proctor.department.dto.MemberDTO;
import dev.varshit.proctor.department.dto.PatchDepartmentRequest;
import dev.varshit.proctor.department.dto.UpdateDepartmentRequest;
import dev.varshit.proctor.department.repository.DepartmentRepository;
import dev.varshit.proctor.persistence.rls.SecureTransaction;
import dev.varshit.proctor.persistence.search.PageParams;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository repository;
    private final SecureTransaction transaction;
    private final CustomFieldService customFields;

    public DepartmentServiceImpl(DepartmentRepository repository, SecureTransaction transaction,
                                 CustomFieldService customFields) {
        this.repository = repository;
        this.transaction = transaction;
        this.customFields = customFields;
    }

    @Override
    public Mono<PageResponse<DepartmentDTO>> query(Map<String, String> filters, PageParams page) {
        return transaction.mono(() -> repository.search(filters, page));
    }

    @Override
    public Mono<DepartmentDTO> get(UUID id) {
        return transaction.mono(() -> load(id));
    }

    @Override
    public Mono<DepartmentDTO> create(CreateDepartmentRequest request) {
        return transaction.mono(() -> insert(request));
    }

    @Override
    public Mono<List<DepartmentDTO>> createBulk(List<CreateDepartmentRequest> requests) {
        return transaction.mono(() -> Flux.fromIterable(requests).concatMap(this::insert).collectList());
    }

    @Override
    public Mono<DepartmentDTO> update(UUID id, UpdateDepartmentRequest request) {
        return transaction.mono(() -> repository
                .update(id, request.name(), request.code(), request.description(), request.activeOrDefault())
                .flatMap(rows -> requireChanged(rows, id))
                .then(applyCustomFields(id, request.customFields(), WriteMode.REPLACE))
                .then(load(id)));
    }

    @Override
    public Mono<DepartmentDTO> patch(UUID id, PatchDepartmentRequest request) {
        return transaction.mono(() -> repository.patch(id, request)
                .flatMap(rows -> requireChanged(rows, id))
                .then(applyCustomFields(id, request.customFields(), WriteMode.MERGE))
                .then(load(id)));
    }

    @Override
    public Mono<Void> delete(UUID id) {
        return transaction.mono(() -> repository.delete(id).flatMap(rows -> requireChanged(rows, id)));
    }

    @Override
    public Flux<MemberDTO> members(UUID id) {
        return transaction.flux(() -> repository.findMembers(id));
    }

    @Override
    public Flux<MemberDTO> addMembers(UUID id, Set<UUID> userIds) {
        return transaction.flux(() -> load(id)
                .then(repository.addMembers(id, userIds))
                .thenMany(repository.findMembers(id)));
    }

    @Override
    public Flux<MemberDTO> removeMembers(UUID id, Set<UUID> userIds) {
        return transaction.flux(() -> load(id)
                .then(repository.removeMembers(id, userIds))
                .thenMany(repository.findMembers(id)));
    }

    private Mono<DepartmentDTO> insert(CreateDepartmentRequest request) {
        UUID id = UUID.randomUUID();
        return repository
                .insert(id, request.name(), request.code(), request.description(), request.activeOrDefault())
                .then(applyCustomFields(id, request.customFields(), WriteMode.CREATE))
                .then(load(id));
    }

    private Mono<Void> applyCustomFields(UUID id, java.util.Map<String, Object> values, WriteMode mode) {
        return customFields.apply(CustomFieldEntity.departments, id, values, mode, CustomFieldContext.ofDepartment(id));
    }

    private Mono<DepartmentDTO> load(UUID id) {
        return repository.findById(id).switchIfEmpty(Mono.error(notFound(id)));
    }

    private Mono<Void> requireChanged(long rows, UUID id) {
        if (rows > 0) {
            return Mono.empty();
        }
        return repository.findById(id)
                .flatMap(existing -> Mono.<Void>error(new ForbiddenException("You are not allowed to modify this department")))
                .switchIfEmpty(Mono.error(notFound(id)));
    }

    private NotFoundException notFound(UUID id) {
        return new NotFoundException("Department " + id + " not found");
    }
}
