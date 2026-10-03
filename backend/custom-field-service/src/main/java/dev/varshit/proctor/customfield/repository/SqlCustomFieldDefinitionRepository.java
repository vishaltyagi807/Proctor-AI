package dev.varshit.proctor.customfield.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.varshit.proctor.common.dto.CustomFieldDefinitionDTO;
import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.common.enums.CustomFieldEntity;
import dev.varshit.proctor.common.enums.CustomFieldType;
import dev.varshit.proctor.common.exception.BadRequestException;
import dev.varshit.proctor.customfield.dto.CreateCustomFieldRequest;
import dev.varshit.proctor.customfield.dto.PatchCustomFieldRequest;
import dev.varshit.proctor.customfield.dto.UpdateCustomFieldRequest;
import dev.varshit.proctor.customfields.CustomFieldSql;
import dev.varshit.proctor.persistence.search.FieldSpec;
import dev.varshit.proctor.persistence.search.PageParams;
import dev.varshit.proctor.persistence.search.PagedQuery;
import dev.varshit.proctor.persistence.search.PagedQueryRunner;
import dev.varshit.proctor.persistence.sql.Params;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Repository
public class SqlCustomFieldDefinitionRepository implements CustomFieldDefinitionRepository {

    private static final PagedQuery QUERY = new PagedQuery(
            CustomFieldSql.DEFINITION_COLUMNS,
            "custom_field_definitions d",
            Map.ofEntries(
                    Map.entry("id", FieldSpec.uuid("d.id")),
                    Map.entry("entity", FieldSpec.enumOf("d.entity", "custom_field_entity", CustomFieldEntity.class)),
                    Map.entry("key", FieldSpec.text("d.key")),
                    Map.entry("label", FieldSpec.text("d.label")),
                    Map.entry("dataType", FieldSpec.enumOf("d.data_type", "custom_field_type", CustomFieldType.class)),
                    Map.entry("required", FieldSpec.bool("d.required")),
                    Map.entry("active", FieldSpec.bool("d.active")),
                    Map.entry("roleId", FieldSpec.uuid("d.applies_to_role_id")),
                    Map.entry("departmentId", FieldSpec.uuid("d.applies_to_department_id")),
                    Map.entry("sortOrder", FieldSpec.integer("d.sort_order")),
                    Map.entry("createdAt", FieldSpec.timestamp("d.created_at"))
            ),
            "d.sort_order",
            "d.id"
    );

    private final SqlGateway gateway;
    private final PagedQueryRunner runner;
    private final ObjectMapper mapper;

    public SqlCustomFieldDefinitionRepository(SqlGateway gateway, PagedQueryRunner runner, ObjectMapper mapper) {
        this.gateway = gateway;
        this.runner = runner;
        this.mapper = mapper;
    }

    @Override
    public Mono<PageResponse<CustomFieldDefinitionDTO>> search(Map<String, String> filters, PageParams page) {
        return runner.run(QUERY, filters, page, CustomFieldDefinitionDTO.class);
    }

    @Override
    public Mono<CustomFieldDefinitionDTO> findById(UUID id) {
        return gateway.queryOne("select " + CustomFieldSql.DEFINITION_COLUMNS + " from custom_field_definitions d where d.id = :id",
                Params.create().with("id", id).build(), CustomFieldDefinitionDTO.class);
    }

    @Override
    public Mono<Long> insert(UUID id, CreateCustomFieldRequest r) {
        return gateway.execute(
                "insert into custom_field_definitions (id, entity, key, label, help_text, data_type, required, options,"
                        + " default_value, min_value, max_value, max_length, pattern, applies_to_role_id,"
                        + " applies_to_department_id, sort_order, active) values (:id, cast(:entity as custom_field_entity),"
                        + " :key, :label, :help, cast(:type as custom_field_type), :required, cast(:options as jsonb),"
                        + " cast(:default as jsonb), :min, :max, :maxLength, :pattern, :role, :department, :sort, :active)",
                Params.create().with("id", id).with("entity", r.entity().name()).with("key", r.key())
                        .with("label", r.label().trim()).withNullable("help", r.helpText(), String.class)
                        .with("type", r.dataType().name()).with("required", Boolean.TRUE.equals(r.required()))
                        .withNullable("options", json(r.options()), String.class)
                        .withNullable("default", json(r.defaultValue()), String.class)
                        .withNullable("min", r.minValue(), BigDecimal.class)
                        .withNullable("max", r.maxValue(), BigDecimal.class)
                        .withNullable("maxLength", r.maxLength(), Integer.class)
                        .withNullable("pattern", blankToNull(r.pattern()), String.class)
                        .withNullable("role", r.appliesToRoleId(), UUID.class)
                        .withNullable("department", r.appliesToDepartmentId(), UUID.class)
                        .with("sort", r.sortOrder() == null ? 0 : r.sortOrder())
                        .with("active", r.active() == null || r.active()).build());
    }

    @Override
    public Mono<Long> update(UUID id, UpdateCustomFieldRequest r) {
        return gateway.execute(
                "update custom_field_definitions set label = :label, help_text = :help, required = :required,"
                        + " options = cast(:options as jsonb), default_value = cast(:default as jsonb),"
                        + " min_value = :min, max_value = :max, max_length = :maxLength, pattern = :pattern,"
                        + " applies_to_role_id = :role, applies_to_department_id = :department,"
                        + " sort_order = :sort, active = :active where id = :id",
                Params.create().with("id", id).with("label", r.label().trim())
                        .withNullable("help", r.helpText(), String.class)
                        .with("required", Boolean.TRUE.equals(r.required()))
                        .withNullable("options", json(r.options()), String.class)
                        .withNullable("default", json(r.defaultValue()), String.class)
                        .withNullable("min", r.minValue(), BigDecimal.class)
                        .withNullable("max", r.maxValue(), BigDecimal.class)
                        .withNullable("maxLength", r.maxLength(), Integer.class)
                        .withNullable("pattern", blankToNull(r.pattern()), String.class)
                        .withNullable("role", r.appliesToRoleId(), UUID.class)
                        .withNullable("department", r.appliesToDepartmentId(), UUID.class)
                        .with("sort", r.sortOrder() == null ? 0 : r.sortOrder())
                        .with("active", r.active() == null || r.active()).build());
    }

    @Override
    public Mono<Long> patch(UUID id, PatchCustomFieldRequest r) {
        return gateway.execute(
                "update custom_field_definitions set label = coalesce(:label, label), help_text = coalesce(:help, help_text),"
                        + " required = coalesce(:required, required), options = coalesce(cast(:options as jsonb), options),"
                        + " sort_order = coalesce(:sort, sort_order), active = coalesce(:active, active) where id = :id",
                Params.create().with("id", id)
                        .withNullable("label", r.label(), String.class)
                        .withNullable("help", r.helpText(), String.class)
                        .withNullable("required", r.required(), Boolean.class)
                        .withNullable("options", json(r.options()), String.class)
                        .withNullable("sort", r.sortOrder(), Integer.class)
                        .withNullable("active", r.active(), Boolean.class).build());
    }

    @Override
    public Mono<Long> delete(UUID id) {
        return gateway.execute("delete from custom_field_definitions where id = :id",
                Params.create().with("id", id).build());
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Invalid value");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
