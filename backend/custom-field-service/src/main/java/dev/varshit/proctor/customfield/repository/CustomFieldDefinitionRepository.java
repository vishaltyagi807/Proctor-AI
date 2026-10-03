package dev.varshit.proctor.customfield.repository;

import dev.varshit.proctor.common.dto.CustomFieldDefinitionDTO;
import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.customfield.dto.CreateCustomFieldRequest;
import dev.varshit.proctor.customfield.dto.PatchCustomFieldRequest;
import dev.varshit.proctor.customfield.dto.UpdateCustomFieldRequest;
import dev.varshit.proctor.persistence.search.PageParams;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

public interface CustomFieldDefinitionRepository {

    Mono<PageResponse<CustomFieldDefinitionDTO>> search(Map<String, String> filters, PageParams page);

    Mono<CustomFieldDefinitionDTO> findById(UUID id);

    Mono<Long> insert(UUID id, CreateCustomFieldRequest request);

    Mono<Long> update(UUID id, UpdateCustomFieldRequest request);

    Mono<Long> patch(UUID id, PatchCustomFieldRequest request);

    Mono<Long> delete(UUID id);
}
