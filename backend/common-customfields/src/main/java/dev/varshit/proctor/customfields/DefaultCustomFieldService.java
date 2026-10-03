package dev.varshit.proctor.customfields;

import dev.varshit.proctor.common.dto.CustomFieldDefinitionDTO;
import dev.varshit.proctor.common.enums.CustomFieldEntity;
import dev.varshit.proctor.common.exception.BadRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DefaultCustomFieldService implements CustomFieldService {

    private final CustomFieldDefinitionReader definitions;
    private final CustomFieldValueStore store;
    private final CustomFieldValidator validator;

    public DefaultCustomFieldService(CustomFieldDefinitionReader definitions, CustomFieldValueStore store,
                                     CustomFieldValidator validator) {
        this.definitions = definitions;
        this.store = store;
        this.validator = validator;
    }

    @Override
    public Flux<CustomFieldDefinitionDTO> applicable(CustomFieldEntity entity, CustomFieldContext context) {
        return definitions.applicable(entity, context);
    }

    @Override
    public Mono<Void> apply(CustomFieldEntity entity, UUID entityId, Map<String, Object> incoming, WriteMode mode,
                            CustomFieldContext context) {
        if (mode != WriteMode.CREATE && (incoming == null || (mode == WriteMode.MERGE && incoming.isEmpty()))) {
            return Mono.empty();
        }
        return definitions.applicable(entity, context).collectList().flatMap(defs -> {
            Map<String, Object> input = incoming == null ? Map.of() : incoming;
            rejectUnknown(defs, input);
            return existing(entity, entityId, mode).flatMap(current -> persist(entity, entityId, mode, defs, input, current));
        });
    }

    private Mono<Map<String, Object>> existing(CustomFieldEntity entity, UUID entityId, WriteMode mode) {
        return mode == WriteMode.MERGE ? store.load(entity, entityId) : Mono.just(Map.of());
    }

    private Mono<Void> persist(CustomFieldEntity entity, UUID entityId, WriteMode mode,
                               List<CustomFieldDefinitionDTO> defs, Map<String, Object> input,
                               Map<String, Object> current) {
        Map<String, Object> candidate = new LinkedHashMap<>(current);
        Set<String> removed = new HashSet<>();
        input.forEach((key, value) -> {
            if (value == null) {
                candidate.remove(key);
                removed.add(key);
            } else {
                candidate.put(key, value);
            }
        });
        if (mode == WriteMode.CREATE) {
            for (CustomFieldDefinitionDTO def : defs) {
                if (!candidate.containsKey(def.key()) && def.defaultValue() != null) {
                    candidate.put(def.key(), def.defaultValue());
                }
            }
        }
        Map<String, Object> normalized = validator.validate(defs, candidate);

        Mono<Void> cleanup = switch (mode) {
            case CREATE -> Mono.empty();
            case REPLACE -> store.deleteExcept(entity, entityId, normalized.keySet()).then();
            case MERGE -> store.deleteKeys(entity, entityId, removed).then();
        };
        Map<String, Object> toWrite = new HashMap<>(normalized);
        if (mode == WriteMode.MERGE) {
            toWrite.keySet().retainAll(input.keySet());
        }
        return cleanup.thenMany(Flux.fromIterable(toWrite.entrySet())
                .concatMap(entry -> store.upsert(entity, entityId, entry.getKey(), entry.getValue()))).then();
    }

    private void rejectUnknown(List<CustomFieldDefinitionDTO> defs, Map<String, Object> input) {
        Set<String> known = new HashSet<>();
        defs.forEach(def -> known.add(def.key()));
        for (String key : input.keySet()) {
            if (!known.contains(key)) {
                throw new BadRequestException("Custom field '" + key + "' is not defined for this record");
            }
        }
    }
}
