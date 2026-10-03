package dev.varshit.proctor.persistence.sql;

import java.util.HashMap;
import java.util.Map;

public final class Params {

    private final Map<String, Object> values = new HashMap<>();

    private Params() {
    }

    public static Params create() {
        return new Params();
    }

    public Params with(String name, Object value) {
        values.put(name, value);
        return this;
    }

    public Params withNullable(String name, Object value, Class<?> type) {
        values.put(name, value == null ? NullValue.of(type) : value);
        return this;
    }

    public Map<String, Object> build() {
        return values;
    }
}
