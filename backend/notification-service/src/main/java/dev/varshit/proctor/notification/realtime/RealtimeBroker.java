package dev.varshit.proctor.notification.realtime;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface RealtimeBroker {

    Mono<Void> publish(UUID userId, String json);

    Flux<ServerSentEvent<String>> subscribe(UUID userId);
}
