package dev.varshit.proctor.persistence.sql;

public record NullValue(Class<?> type) {

    public static NullValue of(Class<?> type) {
        return new NullValue(type);
    }
}
