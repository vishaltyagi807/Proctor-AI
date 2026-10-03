package dev.varshit.proctor.persistence.sql;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface SqlGateway {

    <T> Mono<T> queryOne(String sql, Map<String, Object> params, Class<T> type);

    <T> Flux<T> queryMany(String sql, Map<String, Object> params, Class<T> type);

    Mono<Long> queryLong(String sql, Map<String, Object> params);

    Mono<Long> execute(String sql, Map<String, Object> params);
}
