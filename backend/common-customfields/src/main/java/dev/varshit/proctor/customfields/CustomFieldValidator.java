package dev.varshit.proctor.customfields;

import dev.varshit.proctor.common.dto.CustomFieldDefinitionDTO;
import dev.varshit.proctor.common.exception.BadRequestException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class CustomFieldValidator {

    private static final int DEFAULT_MAX_TEXT = 2000;
    private static final int MAX_MULTI_SELECT = 100;

    public Map<String, Object> validate(List<CustomFieldDefinitionDTO> definitions, Map<String, Object> values) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (CustomFieldDefinitionDTO definition : definitions) {
            Object raw = values.get(definition.key());
            if (raw == null) {
                if (definition.required()) {
                    throw new BadRequestException("Custom field '" + definition.key() + "' is required");
                }
                continue;
            }
            normalized.put(definition.key(), normalize(definition, raw));
        }
        return normalized;
    }

    public Object normalize(CustomFieldDefinitionDTO definition, Object raw) {
        return switch (definition.dataType()) {
            case text -> text(definition, raw);
            case number -> number(definition, raw);
            case bool -> bool(definition, raw);
            case date -> date(definition, raw);
            case select -> select(definition, raw);
            case multi_select -> multiSelect(definition, raw);
        };
    }

    private Object text(CustomFieldDefinitionDTO definition, Object raw) {
        if (!(raw instanceof String value)) {
            throw invalid(definition, "must be text");
        }
        int max = definition.maxLength() == null ? DEFAULT_MAX_TEXT : definition.maxLength();
        if (value.length() > max) {
            throw invalid(definition, "must be at most " + max + " characters");
        }
        if (definition.required() && value.isBlank()) {
            throw invalid(definition, "must not be blank");
        }
        if (definition.pattern() != null && !definition.pattern().isBlank() && !matches(definition, value)) {
            throw invalid(definition, "does not match the required format");
        }
        return value;
    }

    private boolean matches(CustomFieldDefinitionDTO definition, String value) {
        try {
            return Pattern.compile(definition.pattern()).matcher(value).matches();
        } catch (PatternSyntaxException e) {
            throw invalid(definition, "has an invalid validation pattern");
        }
    }

    private Object number(CustomFieldDefinitionDTO definition, Object raw) {
        BigDecimal value;
        try {
            value = raw instanceof Number number ? new BigDecimal(number.toString()) : new BigDecimal(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw invalid(definition, "must be a number");
        }
        if (definition.minValue() != null && value.compareTo(definition.minValue()) < 0) {
            throw invalid(definition, "must be at least " + definition.minValue().toPlainString());
        }
        if (definition.maxValue() != null && value.compareTo(definition.maxValue()) > 0) {
            throw invalid(definition, "must be at most " + definition.maxValue().toPlainString());
        }
        return value.stripTrailingZeros();
    }

    private Object bool(CustomFieldDefinitionDTO definition, Object raw) {
        if (raw instanceof Boolean value) {
            return value;
        }
        String text = String.valueOf(raw).trim();
        if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")) {
            return Boolean.valueOf(text);
        }
        throw invalid(definition, "must be true or false");
    }

    private Object date(CustomFieldDefinitionDTO definition, Object raw) {
        try {
            return LocalDate.parse(String.valueOf(raw).trim()).toString();
        } catch (DateTimeParseException e) {
            throw invalid(definition, "must be a date in yyyy-MM-dd format");
        }
    }

    private Object select(CustomFieldDefinitionDTO definition, Object raw) {
        String value = String.valueOf(raw);
        if (definition.options() == null || !definition.options().contains(value)) {
            throw invalid(definition, "must be one of " + definition.options());
        }
        return value;
    }

    private Object multiSelect(CustomFieldDefinitionDTO definition, Object raw) {
        if (!(raw instanceof List<?> items)) {
            throw invalid(definition, "must be a list");
        }
        if (items.size() > MAX_MULTI_SELECT) {
            throw invalid(definition, "has too many values");
        }
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (Object item : items) {
            String value = String.valueOf(item);
            if (definition.options() == null || !definition.options().contains(value)) {
                throw invalid(definition, "contains an unknown option '" + value + "'");
            }
            selected.add(value);
        }
        if (definition.required() && selected.isEmpty()) {
            throw invalid(definition, "must not be empty");
        }
        return new ArrayList<>(selected);
    }

    private BadRequestException invalid(CustomFieldDefinitionDTO definition, String message) {
        return new BadRequestException("Custom field '" + definition.key() + "' " + message);
    }
}
