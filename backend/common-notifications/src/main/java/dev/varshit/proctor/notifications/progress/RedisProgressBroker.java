package dev.varshit.proctor.notifications.progress;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

public class RedisProgressBroker implements ProgressBroker {

    private static final Duration HEARTBEAT = Duration.ofSeconds(15);
    private static final String CHANNEL_PREFIX = "progress:user:";

    private final ReactiveStringRedisTemplate redis;
    private final ReactiveRedisMessageListenerContainer container;
    private final ObjectMapper mapper;

    public RedisProgressBroker(ReactiveStringRedisTemplate redis, ReactiveRedisMessageListenerContainer container,
                               ObjectMapper mapper) {
        this.redis = redis;
        this.container = container;
        this.mapper = mapper;
    }

    private static String channel(UUID userId) {
        return CHANNEL_PREFIX + userId;
    }

    @Override
    public Mono<Void> publish(UUID userId, String event, Object data) {
        return Mono.fromCallable(() -> mapper.writeValueAsString(Map.of("event", event, "data", data)))
                .flatMap(json -> redis.convertAndSend(channel(userId), json))
                .then();
    }

    @Override
    public Flux<ServerSentEvent<Object>> subscribe(UUID userId) {
        Flux<ServerSentEvent<Object>> messages = container.receive(ChannelTopic.of(channel(userId)))
                .map(ReactiveSubscription.Message::getMessage)
                .flatMap(this::toEvent);
        Flux<ServerSentEvent<Object>> heartbeat = Flux.interval(HEARTBEAT)
                .map(tick -> ServerSentEvent.<Object>builder().event("heartbeat").data("ping").build());
        Flux<ServerSentEvent<Object>> ready = Flux.just(ServerSentEvent.<Object>builder().event("ready").data("connected").build());
        return Flux.merge(ready, messages, heartbeat);
    }

    private Mono<ServerSentEvent<Object>> toEvent(String json) {
        return Mono.fromCallable(() -> {
                    Map<String, Object> envelope = mapper.readValue(json, new TypeReference<Map<String, Object>>() {
                    });
                    return ServerSentEvent.<Object>builder()
                            .event((String) envelope.get("event"))
                            .data(envelope.get("data"))
                            .build();
                })
                .onErrorResume(error -> Mono.empty());
    }
}
