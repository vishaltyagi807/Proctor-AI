package dev.varshit.proctor.customfields;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.varshit.proctor.common.enums.CustomFieldEntity;
import dev.varshit.proctor.common.exception.BadRequestException;
import dev.varshit.proctor.persistence.sql.Params;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SqlCustomFieldValueStore implements CustomFieldValueStore {

    private final SqlGateway gateway;
    private final ObjectMapper mapper;

    public SqlCustomFieldValueStore(SqlGateway gateway, ObjectMapper mapper) {
        this.gateway = gateway;
        this.mapper = mapper;
    }

    @Override
    public Mono<Map<String, Object>> load(CustomFieldEntity entity, UUID entityId) {
        return gateway.queryOne(
                "select (" + CustomFieldSql.valuesExpression(entity.name(), ":id") + ") as values",
                Params.create().with("id", entityId).build(), ValuesRow.class)
                .map(row -> row.values() == null ? Map.<String, Object>of() : row.values())
                .defaultIfEmpty(Map.of());
    }

    @Override
    public Mono<Long> upsert(CustomFieldEntity entity, UUID entityId, String key, Object value) {
        return gateway.execute(
                "insert into custom_field_values (definition_id, entity, entity_id, value)"
                        + " select d.id, d.entity, :id, cast(:value as jsonb) from custom_field_definitions d"
                        + " where d.entity = cast(:entity as custom_field_entity) and d.key = :key"
                        + " on conflict (definition_id, entity_id) do update set value = excluded.value",
                Params.create().with("id", entityId).with("entity", entity.name()).with("key", key)
                        .with("value", json(value)).build());
    }

    @Override
    public Mono<Long> deleteKeys(CustomFieldEntity entity, UUID entityId, Set<String> keys) {
        if (keys.isEmpty()) {
            return Mono.just(0L);
        }
        return gateway.execute(
                "delete from custom_field_values cv using custom_field_definitions d"
                        + " where d.id = cv.definition_id and cv.entity_id = :id"
                        + " and d.entity = cast(:entity as custom_field_entity) and d.key = any(:keys)",
                Params.create().with("id", entityId).with("entity", entity.name())
                        .with("keys", keys.toArray(String[]::new)).build());
    }

    @Override
    public Mono<Long> deleteExcept(CustomFieldEntity entity, UUID entityId, Set<String> keepKeys) {
        return gateway.execute(
                "delete from custom_field_values cv using custom_field_definitions d"
                        + " where d.id = cv.definition_id and cv.entity_id = :id"
                        + " and d.entity = cast(:entity as custom_field_entity) and not (d.key = any(:keys))",
                Params.create().with("id", entityId).with("entity", entity.name())
                        .with("keys", keepKeys.toArray(String[]::new)).build());
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Invalid custom field value");
        }
    }

    private record ValuesRow(HashMap<String, Object> values) {
    }
}
