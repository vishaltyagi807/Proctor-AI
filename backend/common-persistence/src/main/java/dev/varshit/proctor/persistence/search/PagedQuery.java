package dev.varshit.proctor.persistence.search;

import java.util.Map;

public record PagedQuery(
        String select,
        String from,
        Map<String, FieldSpec> fields,
        String defaultSort,
        String tieBreaker,
        String customFieldsExpression
) {
    public PagedQuery(String select, String from, Map<String, FieldSpec> fields, String defaultSort,
                      String tieBreaker) {
        this(select, from, fields, defaultSort, tieBreaker, null);
    }
}
