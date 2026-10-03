package dev.varshit.proctor.customfields;

import dev.varshit.proctor.common.dto.CustomFieldDefinitionDTO;
import dev.varshit.proctor.common.enums.CustomFieldEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

public interface CustomFieldService {

    Flux<CustomFieldDefinitionDTO> applicable(CustomFieldEntity entity, CustomFieldContext context);

    Mono<Void> apply(CustomFieldEntity entity, UUID entityId, Map<String, Object> incoming, WriteMode mode,
                     CustomFieldContext context);
}
