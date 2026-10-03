package dev.varshit.proctor.notifications.progress;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Cross-service, non-persisted progress channel for long-running background work (bulk imports,
 * batch enrollments, ...). Backed by Redis pub/sub - any service can publish, whoever serves the
 * SSE endpoint subscribes. Unlike {@code NotificationPublisher}, nothing here is written to the
 * notifications table: this is a transient status feed, not a durable notification.
 */
public interface ProgressBroker {

    Flux<ServerSentEvent<Object>> subscribe(UUID userId);

    Mono<Void> publish(UUID userId, String event, Object data);
}
