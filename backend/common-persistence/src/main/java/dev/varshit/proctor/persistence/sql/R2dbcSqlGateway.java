package dev.varshit.proctor.persistence.sql;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

public class R2dbcSqlGateway implements SqlGateway {

    private static final String JSON_COLUMN = "j";

    private final DatabaseClient client;
    private final ObjectMapper rowMapper = JsonMapper.builder()
            .findAndAddModules()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public R2dbcSqlGateway(DatabaseClient client) {
        this.client = client;
    }

    @Override
    public <T> Mono<T> queryOne(String sql, Map<String, Object> params, Class<T> type) {
        return queryMany(sql, params, type).next();
    }

    @Override
    public <T> Flux<T> queryMany(String sql, Map<String, Object> params, Class<T> type) {
        String wrapped = "select to_jsonb(x)::text as " + JSON_COLUMN + " from (" + sql + ") x";
        return bind(wrapped, params)
                .map(row -> row.get(JSON_COLUMN, String.class))
                .all()
                .map(json -> read(json, type));
    }

    @Override
    public Mono<Long> queryLong(String sql, Map<String, Object> params) {
        return bind(sql, params)
                .map(row -> row.get(0, Long.class))
                .one();
    }

    @Override
    public Mono<Long> execute(String sql, Map<String, Object> params) {
        return bind(sql, params).fetch().rowsUpdated();
    }

    private DatabaseClient.GenericExecuteSpec bind(String sql, Map<String, Object> params) {
        DatabaseClient.GenericExecuteSpec spec = client.sql(sql);
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Object value = entry.getValue();
            spec = value instanceof NullValue nv
                    ? spec.bindNull(entry.getKey(), nv.type())
                    : spec.bind(entry.getKey(), value);
        }
        return spec;
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return rowMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to map database row to " + type.getSimpleName(), e);
        }
    }
}
