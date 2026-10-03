package dev.varshit.proctor.customfield.service;

import dev.varshit.proctor.common.dto.CustomFieldDefinitionDTO;
import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.common.enums.CustomFieldEntity;
import dev.varshit.proctor.customfield.dto.CreateCustomFieldRequest;
import dev.varshit.proctor.customfield.dto.PatchCustomFieldRequest;
import dev.varshit.proctor.customfield.dto.UpdateCustomFieldRequest;
import dev.varshit.proctor.persistence.search.PageParams;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface CustomFieldDefinitionService {

    Mono<PageResponse<CustomFieldDefinitionDTO>> query(Map<String, String> filters, PageParams page);

    Flux<CustomFieldDefinitionDTO> applicable(CustomFieldEntity entity, Set<UUID> roleIds, Set<UUID> departmentIds);

    Mono<CustomFieldDefinitionDTO> get(UUID id);

    Mono<CustomFieldDefinitionDTO> create(CreateCustomFieldRequest request);

    Mono<CustomFieldDefinitionDTO> update(UUID id, UpdateCustomFieldRequest request);

    Mono<CustomFieldDefinitionDTO> patch(UUID id, PatchCustomFieldRequest request);

    Mono<Void> delete(UUID id);
}
