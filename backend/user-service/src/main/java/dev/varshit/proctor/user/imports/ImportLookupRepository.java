package dev.varshit.proctor.user.imports;

import dev.varshit.proctor.common.enums.CustomFieldType;
import dev.varshit.proctor.persistence.sql.Params;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import dev.varshit.proctor.user.imports.ImportReferenceData.CustomFieldMeta;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves the human-friendly keys the import file format uses (role names, department codes,
 * custom field keys) so the parser and the per-row import loop never need to know real UUIDs.
 */
@Repository
public class ImportLookupRepository {

    private record RoleRow(UUID id, String name) {
    }

    private record DepartmentRow(UUID id, String code) {
    }

    private record CustomFieldRow(String key, CustomFieldType dataType, List<String> options) {
    }

    private final SqlGateway gateway;

    public ImportLookupRepository(SqlGateway gateway) {
        this.gateway = gateway;
    }

    public Mono<Map<String, UUID>> roleIdsByName() {
        return gateway.queryMany("select id, name from roles", Params.create().build(), RoleRow.class)
                .collectMap(row -> row.name().toLowerCase(Locale.ROOT), RoleRow::id);
    }

    public Mono<Map<String, UUID>> departmentIdsByCode() {
        return gateway.queryMany("select id, code from departments", Params.create().build(), DepartmentRow.class)
                .collectMap(row -> row.code().toLowerCase(Locale.ROOT), DepartmentRow::id);
    }

    public Mono<Map<String, CustomFieldMeta>> customFieldsByKey() {
        return gateway.queryMany(
                        "select key, data_type, options from custom_field_definitions where entity = 'users'::custom_field_entity and active",
                        Params.create().build(), CustomFieldRow.class)
                .collectMap(CustomFieldRow::key, row -> new CustomFieldMeta(row.dataType(), row.options()));
    }

    public Mono<ImportReferenceData> load() {
        return Mono.zip(roleIdsByName(), departmentIdsByCode(), customFieldsByKey())
                .map(tuple -> new ImportReferenceData(tuple.getT1(), tuple.getT2(), tuple.getT3()));
    }
}
