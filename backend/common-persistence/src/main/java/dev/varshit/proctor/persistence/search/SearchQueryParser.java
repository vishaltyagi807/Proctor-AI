package dev.varshit.proctor.persistence.search;

import dev.varshit.proctor.common.exception.BadRequestException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SearchQueryParser {

    private static final Set<String> RESERVED = Set.of("page", "size", "sortBy", "direction");
    private static final Set<String> OPERATORS = Set.of(
            "eq", "neq", "gt", "gte", "lt", "lte", "like", "ilike", "in", "between", "is");
    private static final String OR_KEY = "or";
    private static final int MAX_IN_VALUES = 100;
    private static final String CUSTOM_PREFIX = "cf.";
    private static final java.util.regex.Pattern CUSTOM_KEY = java.util.regex.Pattern.compile("[a-z][a-z0-9_]{0,49}");
    private static final String NUMERIC_TEST = "^-?[0-9]+(\\.[0-9]+)?$";

    public SqlCondition parse(Map<String, String> query, Map<String, FieldSpec> fields) {
        return parse(query, fields, null);
    }

    public SqlCondition parse(Map<String, String> query, Map<String, FieldSpec> fields, String customFieldsExpression) {
        Builder builder = new Builder(fields, customFieldsExpression);
        List<String> clauses = new ArrayList<>();
        for (Map.Entry<String, String> entry : query.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (RESERVED.contains(key) || value == null) {
                continue;
            }
            if (OR_KEY.equals(key)) {
                clauses.add(builder.orGroup(value));
            } else {
                clauses.add(builder.condition(key, value));
            }
        }
        if (clauses.isEmpty()) {
            return SqlCondition.none();
        }
        return new SqlCondition(String.join(" and ", clauses), builder.params);
    }

    private static final class Builder {

        private final Map<String, FieldSpec> fields;
        private final String customFieldsExpression;
        private final Map<String, Object> params = new HashMap<>();
        private int counter;

        private Builder(Map<String, FieldSpec> fields, String customFieldsExpression) {
            this.fields = fields;
            this.customFieldsExpression = customFieldsExpression;
        }

        private String custom(String key, String raw) {
            String name = key.substring(CUSTOM_PREFIX.length());
            if (customFieldsExpression == null || !CUSTOM_KEY.matcher(name).matches()) {
                throw new BadRequestException("Unknown filter field '" + key + "'");
            }
            int dot = raw.indexOf('.');
            String operator = "eq";
            String value = raw;
            if (dot > 0 && OPERATORS.contains(raw.substring(0, dot))) {
                operator = raw.substring(0, dot);
                value = raw.substring(dot + 1);
            }
            String keyParam = "f" + counter++;
            params.put(keyParam, name);
            String expression = "((" + customFieldsExpression + ") ->> :" + keyParam + ")";
            return switch (operator) {
                case "eq" -> expression + " = " + textBind(value);
                case "neq" -> expression + " <> " + textBind(value);
                case "like" -> expression + " like " + textBind(value);
                case "ilike" -> expression + " ilike " + textBind(value);
                case "in" -> {
                    List<String> placeholders = new ArrayList<>();
                    for (String item : value.replaceAll("^\\(|\\)$", "").split(",")) {
                        placeholders.add(textBind(item.trim()));
                    }
                    yield expression + " in (" + String.join(", ", placeholders) + ")";
                }
                case "gt" -> numeric(expression, ">", value);
                case "gte" -> numeric(expression, ">=", value);
                case "lt" -> numeric(expression, "<", value);
                case "lte" -> numeric(expression, "<=", value);
                case "is" -> nullCheck(expression, value);
                default -> throw new BadRequestException("Unsupported operator '" + operator + "' for custom fields");
            };
        }

        private String textBind(String value) {
            String name = "f" + counter++;
            params.put(name, value);
            return ":" + name;
        }

        private String numeric(String expression, String comparator, String value) {
            String name = "f" + counter++;
            try {
                params.put(name, new java.math.BigDecimal(value));
            } catch (NumberFormatException e) {
                throw new BadRequestException("Custom field comparison requires a number");
            }
            return "(case when " + expression + " ~ '" + NUMERIC_TEST + "' then cast(" + expression
                    + " as numeric) end) " + comparator + " :" + name;
        }

        private String orGroup(String value) {
            String inner = value.replaceAll("^\\(|\\)$", "");
            List<String> parts = new ArrayList<>();
            for (String part : inner.split(",")) {
                String[] tokens = part.split("\\.", 3);
                if (tokens.length < 3) {
                    throw new BadRequestException("Invalid 'or' condition");
                }
                parts.add(build(tokens[0], tokens[1], tokens[2]));
            }
            return "(" + String.join(" or ", parts) + ")";
        }

        private String condition(String field, String raw) {
            if (field.startsWith(CUSTOM_PREFIX)) {
                return custom(field, raw);
            }
            int dot = raw.indexOf('.');
            if (dot > 0 && OPERATORS.contains(raw.substring(0, dot))) {
                return build(field, raw.substring(0, dot), raw.substring(dot + 1));
            }
            return build(field, "eq", raw);
        }

        private String build(String field, String operator, String raw) {
            FieldSpec spec = fields.get(field);
            if (spec == null) {
                throw new BadRequestException("Unknown filter field '" + field + "'");
            }
            if (spec.membership() != null) {
                return member(spec, field, operator, raw);
            }
            String column = spec.column();
            return switch (operator) {
                case "eq" -> column + " = " + bind(spec, field, raw);
                case "neq" -> column + " <> " + bind(spec, field, raw);
                case "gt" -> column + " > " + bind(spec, field, raw);
                case "gte" -> column + " >= " + bind(spec, field, raw);
                case "lt" -> column + " < " + bind(spec, field, raw);
                case "lte" -> column + " <= " + bind(spec, field, raw);
                case "like" -> textOnly(spec, field) + column + " like " + bind(spec, field, raw);
                case "ilike" -> textOnly(spec, field) + column + " ilike " + bind(spec, field, raw);
                case "in" -> in(spec, field, raw);
                case "between" -> between(spec, field, raw);
                case "is" -> nullCheck(column, raw);
                default -> throw new BadRequestException("Unsupported operator '" + operator + "'");
            };
        }

        private String member(FieldSpec spec, String field, String operator, String raw) {
            String template = spec.membership();
            return switch (operator) {
                case "eq" -> template.formatted(spec.column() + " = " + bind(spec, field, raw));
                case "neq" -> "not " + template.formatted(spec.column() + " = " + bind(spec, field, raw));
                case "in" -> template.formatted(in(spec, field, raw));
                case "is" -> switch (raw) {
                    case "null" -> "not " + template.formatted("true");
                    case "notnull" -> template.formatted("true");
                    default -> throw new BadRequestException("Use is.null or is.notnull");
                };
                default -> throw new BadRequestException("Operator not supported for '" + field + "'");
            };
        }

        private String textOnly(FieldSpec spec, String field) {
            if (spec.kind() != FieldKind.TEXT) {
                throw new BadRequestException("Operator not supported for '" + field + "'");
            }
            return "";
        }

        private String in(FieldSpec spec, String field, String raw) {
            String[] values = raw.replaceAll("^\\(|\\)$", "").split(",");
            if (values.length == 0 || values.length > MAX_IN_VALUES) {
                throw new BadRequestException("Invalid 'in' list for '" + field + "'");
            }
            List<String> placeholders = new ArrayList<>();
            for (String value : values) {
                placeholders.add(bind(spec, field, value.trim()));
            }
            return spec.column() + " in (" + String.join(", ", placeholders) + ")";
        }

        private String between(FieldSpec spec, String field, String raw) {
            String[] bounds = raw.split(",");
            if (bounds.length != 2) {
                throw new BadRequestException("Invalid 'between' range for '" + field + "'");
            }
            return spec.column() + " between " + bind(spec, field, bounds[0].trim())
                    + " and " + bind(spec, field, bounds[1].trim());
        }

        private String nullCheck(String column, String raw) {
            return switch (raw) {
                case "null" -> column + " is null";
                case "notnull" -> column + " is not null";
                default -> throw new BadRequestException("Use is.null or is.notnull");
            };
        }

        private String bind(FieldSpec spec, String field, String raw) {
            String name = "f" + counter++;
            params.put(name, spec.convert(field, raw));
            return spec.placeholder(name);
        }
    }
}
