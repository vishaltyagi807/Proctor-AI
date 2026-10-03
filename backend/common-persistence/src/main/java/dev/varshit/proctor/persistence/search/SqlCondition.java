package dev.varshit.proctor.persistence.search;

import java.util.Map;

public record SqlCondition(String sql, Map<String, Object> params) {

    public static SqlCondition none() {
        return new SqlCondition("true", Map.of());
    }
}
