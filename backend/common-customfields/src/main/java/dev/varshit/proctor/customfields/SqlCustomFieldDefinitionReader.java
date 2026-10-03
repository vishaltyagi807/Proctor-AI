package dev.varshit.proctor.customfields;

import dev.varshit.proctor.common.dto.CustomFieldDefinitionDTO;
import dev.varshit.proctor.common.enums.CustomFieldEntity;
import dev.varshit.proctor.persistence.sql.Params;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import reactor.core.publisher.Flux;

import java.util.UUID;

public class SqlCustomFieldDefinitionReader implements CustomFieldDefinitionReader {

    private final SqlGateway gateway;

    public SqlCustomFieldDefinitionReader(SqlGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public Flux<CustomFieldDefinitionDTO> applicable(CustomFieldEntity entity, CustomFieldContext context) {
        return gateway.queryMany(
                "select " + CustomFieldSql.DEFINITION_COLUMNS + " from custom_field_definitions d"
                        + " where d.entity = cast(:entity as custom_field_entity) and d.active"
                        + " and (d.applies_to_role_id is null or d.applies_to_role_id = any(:roles))"
                        + " and (d.applies_to_department_id is null or d.applies_to_department_id = any(:departments))"
                        + " order by d.sort_order, d.key",
                Params.create().with("entity", entity.name())
                        .with("roles", context.roleIds().toArray(UUID[]::new))
                        .with("departments", context.departmentIds().toArray(UUID[]::new)).build(),
                CustomFieldDefinitionDTO.class);
    }
}
