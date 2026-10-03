package dev.varshit.proctor.customfields;

public final class CustomFieldSql {

    public static final String DEFINITION_COLUMNS = "d.id, d.entity, d.key, d.label, d.help_text, d.data_type,"
            + " d.required, d.options, d.default_value, d.min_value, d.max_value, d.max_length, d.pattern,"
            + " d.applies_to_role_id, d.applies_to_department_id, d.sort_order, d.active, d.created_at, d.updated_at";

    private CustomFieldSql() {
    }

    public static String valuesExpression(String entity, String idColumn) {
        return "select coalesce(jsonb_object_agg(cd.key, cv.value), '{}'::jsonb)"
                + " from custom_field_values cv join custom_field_definitions cd on cd.id = cv.definition_id"
                + " where cv.entity = '" + entity + "' and cv.entity_id = " + idColumn + " and cd.active";
    }

    public static String valuesColumn(String entity, String idColumn) {
        return "(" + valuesExpression(entity, idColumn) + ") as custom_fields";
    }
}
