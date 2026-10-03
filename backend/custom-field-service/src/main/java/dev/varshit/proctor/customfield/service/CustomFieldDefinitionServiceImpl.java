package dev.varshit.proctor.customfield.service;

import dev.varshit.proctor.common.dto.CustomFieldDefinitionDTO;
import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.common.enums.CustomFieldEntity;
import dev.varshit.proctor.common.exception.ForbiddenException;
import dev.varshit.proctor.common.exception.NotFoundException;
import dev.varshit.proctor.customfield.dto.CreateCustomFieldRequest;
import dev.varshit.proctor.customfield.dto.PatchCustomFieldRequest;
import dev.varshit.proctor.customfield.dto.UpdateCustomFieldRequest;
import dev.varshit.proctor.customfield.repository.CustomFieldDefinitionRepository;
import dev.varshit.proctor.customfields.CustomFieldContext;
import dev.varshit.proctor.customfields.CustomFieldService;
import dev.varshit.proctor.persistence.rls.SecureTransaction;
import dev.varshit.proctor.persistence.search.PageParams;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CustomFieldDefinitionServiceImpl implements CustomFieldDefinitionService {

    private final CustomFieldDefinitionRepository repository;
    private final CustomFieldService customFields;
    private final DefinitionRules rules;
    private final SecureTransaction transaction;

    public CustomFieldDefinitionServiceImpl(CustomFieldDefinitionRepository repository, CustomFieldService customFields,
                                            DefinitionRules rules, SecureTransaction transaction) {
        this.repository = repository;
        this.customFields = customFields;
        this.rules = rules;
        this.transaction = transaction;
    }

    @Override
    public Mono<PageResponse<CustomFieldDefinitionDTO>> query(Map<String, String> filters, PageParams page) {
        return transaction.mono(() -> repository.search(filters, page));
    }

    @Override
    public Flux<CustomFieldDefinitionDTO> applicable(CustomFieldEntity entity, Set<UUID> roleIds,
                                                     Set<UUID> departmentIds) {
        return transaction.flux(() -> customFields.applicable(entity, new CustomFieldContext(roleIds, departmentIds)));
    }

    @Override
    public Mono<CustomFieldDefinitionDTO> get(UUID id) {
        return transaction.mono(() -> load(id));
    }

    @Override
    public Mono<CustomFieldDefinitionDTO> create(CreateCustomFieldRequest request) {
        rules.validateCreate(request);
        UUID id = UUID.randomUUID();
        return transaction.mono(() -> repository.insert(id, request).then(load(id)));
    }

    @Override
    public Mono<CustomFieldDefinitionDTO> update(UUID id, UpdateCustomFieldRequest request) {
        return transaction.mono(() -> load(id).doOnNext(existing -> rules.validateUpdate(existing, request))
                .then(repository.update(id, request))
                .flatMap(rows -> requireChanged(rows, id))
                .then(load(id)));
    }

    @Override
    public Mono<CustomFieldDefinitionDTO> patch(UUID id, PatchCustomFieldRequest request) {
        return transaction.mono(() -> repository.patch(id, request)
                .flatMap(rows -> requireChanged(rows, id))
                .then(load(id)));
    }

    @Override
    public Mono<Void> delete(UUID id) {
        return transaction.mono(() -> repository.delete(id).flatMap(rows -> requireChanged(rows, id)));
    }

    private Mono<CustomFieldDefinitionDTO> load(UUID id) {
        return repository.findById(id).switchIfEmpty(Mono.error(notFound(id)));
    }

    private Mono<Void> requireChanged(long rows, UUID id) {
        if (rows > 0) {
            return Mono.empty();
        }
        return repository.findById(id)
                .flatMap(existing -> Mono.<Void>error(new ForbiddenException("You are not allowed to modify custom fields")))
                .switchIfEmpty(Mono.error(notFound(id)));
    }

    private NotFoundException notFound(UUID id) {
        return new NotFoundException("Custom field " + id + " not found");
    }
}
