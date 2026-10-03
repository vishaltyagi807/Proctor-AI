package dev.varshit.proctor.notification.realtime;

import dev.varshit.proctor.notifications.NotificationTopics;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@Component
public class RedisRealtimeBroker implements RealtimeBroker {

    private static final Duration HEARTBEAT = Duration.ofSeconds(15);

    private final ReactiveStringRedisTemplate redis;
    private final ReactiveRedisMessageListenerContainer container;

    public RedisRealtimeBroker(ReactiveStringRedisTemplate redis, ReactiveRedisMessageListenerContainer container) {
        this.redis = redis;
        this.container = container;
    }

    @Override
    public Mono<Void> publish(UUID userId, String json) {
        return redis.convertAndSend(NotificationTopics.userChannel(userId), json).then();
    }

    @Override
    public Flux<ServerSentEvent<String>> subscribe(UUID userId) {
        Flux<ServerSentEvent<String>> messages = container
                .receive(ChannelTopic.of(NotificationTopics.userChannel(userId)))
                .map(ReactiveSubscription.Message::getMessage)
                .map(json -> ServerSentEvent.<String>builder().event("notification").data(json).build());
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(HEARTBEAT)
                .map(tick -> ServerSentEvent.<String>builder().event("heartbeat").data("ping").build());
        Flux<ServerSentEvent<String>> ready = Flux.just(ServerSentEvent.<String>builder().event("ready").data("connected").build());
        return Flux.merge(ready, messages, heartbeat);
    }
}
