package dev.varshit.proctor.customfields;

import dev.varshit.proctor.common.dto.CustomFieldDefinitionDTO;
import dev.varshit.proctor.common.enums.CustomFieldEntity;
import reactor.core.publisher.Flux;

public interface CustomFieldDefinitionReader {

    Flux<CustomFieldDefinitionDTO> applicable(CustomFieldEntity entity, CustomFieldContext context);
}
