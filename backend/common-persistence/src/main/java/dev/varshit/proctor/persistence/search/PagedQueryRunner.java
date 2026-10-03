package dev.varshit.proctor.persistence.search;

import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.persistence.sql.SqlGateway;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PagedQueryRunner {

    private final SqlGateway gateway;
    private final SearchQueryParser parser;

    public PagedQueryRunner(SqlGateway gateway, SearchQueryParser parser) {
        this.gateway = gateway;
        this.parser = parser;
    }

    public <T> Mono<PageResponse<T>> run(
            PagedQuery query,
            Map<String, String> filters,
            PageParams page,
            Class<T> type
    ) {
        SqlCondition condition = parser.parse(filters, query.fields(), query.customFieldsExpression());
        String sortColumn = resolveSort(query, page);
        String direction = page.descending() ? "desc" : "asc";

        Map<String, Object> pageParams = new HashMap<>(condition.params());
        pageParams.put("pageLimit", page.size());
        pageParams.put("pageOffset", page.offset());

        String pageSql = "select " + query.select() + " from " + query.from()
                + " where " + condition.sql()
                + " order by " + sortColumn + " " + direction + ", " + query.tieBreaker() + " asc"
                + " limit :pageLimit offset :pageOffset";
        String countSql = "select count(*) from " + query.from() + " where " + condition.sql();

        return gateway.queryLong(countSql, condition.params())
                .flatMap(total -> gateway.queryMany(pageSql, pageParams, type)
                        .collectList()
                        .map((List<T> rows) -> PageResponse.of(rows, total, page.page(), page.size())));
    }

    private String resolveSort(PagedQuery query, PageParams page) {
        FieldSpec spec = page.sortBy() == null ? null : query.fields().get(page.sortBy());
        return spec == null ? query.defaultSort() : spec.column();
    }
}
