package dev.varshit.proctor.persistence.search;

import dev.varshit.proctor.common.exception.BadRequestException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public record FieldSpec(String column, FieldKind kind, String enumType, Set<String> allowed, String membership) {

    public static FieldSpec text(String column) {
        return new FieldSpec(column, FieldKind.TEXT, null, Set.of(), null);
    }

    public static FieldSpec uuid(String column) {
        return new FieldSpec(column, FieldKind.UUID, null, Set.of(), null);
    }

    public static FieldSpec bool(String column) {
        return new FieldSpec(column, FieldKind.BOOLEAN, null, Set.of(), null);
    }

    public static FieldSpec integer(String column) {
        return new FieldSpec(column, FieldKind.INTEGER, null, Set.of(), null);
    }

    public static FieldSpec timestamp(String column) {
        return new FieldSpec(column, FieldKind.TIMESTAMP, null, Set.of(), null);
    }

    public static FieldSpec enumOf(String column, String enumType, Class<? extends Enum<?>> type) {
        Set<String> names = Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
        return new FieldSpec(column, FieldKind.ENUM, enumType, names, null);
    }

    public static FieldSpec uuidMember(String table, String ownerColumn, String owner, String valueColumn) {
        return new FieldSpec("m." + valueColumn, FieldKind.UUID, null, Set.of(),
                "exists(select 1 from " + table + " m where m." + ownerColumn + " = " + owner + " and %s)");
    }

    public String placeholder(String name) {
        return kind == FieldKind.ENUM ? "cast(:" + name + " as " + enumType + ")" : ":" + name;
    }

    public Object convert(String field, String raw) {
        try {
            return switch (kind) {
                case TEXT -> raw;
                case UUID -> java.util.UUID.fromString(raw);
                case BOOLEAN -> parseBoolean(raw);
                case INTEGER -> Integer.valueOf(raw);
                case TIMESTAMP -> parseTimestamp(raw);
                case ENUM -> parseEnum(raw);
            };
        } catch (BadRequestException e) {
            throw new BadRequestException("Invalid value for '" + field + "'");
        } catch (RuntimeException e) {
            throw new BadRequestException("Invalid value for '" + field + "'");
        }
    }

    private Boolean parseBoolean(String raw) {
        if (!raw.equalsIgnoreCase("true") && !raw.equalsIgnoreCase("false")) {
            throw new BadRequestException("boolean");
        }
        return Boolean.valueOf(raw);
    }

    private OffsetDateTime parseTimestamp(String raw) {
        try {
            return OffsetDateTime.parse(raw);
        } catch (RuntimeException e) {
            return LocalDate.parse(raw).atStartOfDay().atOffset(ZoneOffset.UTC);
        }
    }

    private String parseEnum(String raw) {
        if (!allowed.contains(raw)) {
            throw new BadRequestException("enum");
        }
        return raw;
    }
}
