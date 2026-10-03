package dev.varshit.proctor.customfields;

import dev.varshit.proctor.common.enums.CustomFieldEntity;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface CustomFieldValueStore {

    Mono<Map<String, Object>> load(CustomFieldEntity entity, UUID entityId);

    Mono<Long> upsert(CustomFieldEntity entity, UUID entityId, String key, Object value);

    Mono<Long> deleteKeys(CustomFieldEntity entity, UUID entityId, Set<String> keys);

    Mono<Long> deleteExcept(CustomFieldEntity entity, UUID entityId, Set<String> keepKeys);
}
