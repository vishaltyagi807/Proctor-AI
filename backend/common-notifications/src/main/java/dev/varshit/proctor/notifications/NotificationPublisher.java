package dev.varshit.proctor.notifications;

import reactor.core.publisher.Mono;

public interface NotificationPublisher {

    Mono<Void> publish(NotificationEvent event);
}
