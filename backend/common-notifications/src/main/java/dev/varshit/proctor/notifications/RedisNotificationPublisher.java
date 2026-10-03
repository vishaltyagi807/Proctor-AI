package dev.varshit.proctor.notifications;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.util.Map;

public class RedisNotificationPublisher implements NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(RedisNotificationPublisher.class);
    private static final long MAX_STREAM_LENGTH = 100_000;

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final String source;

    public RedisNotificationPublisher(ReactiveStringRedisTemplate redis, ObjectMapper mapper, String source) {
        this.redis = redis;
        this.mapper = mapper;
        this.source = source;
    }

    @Override
    public Mono<Void> publish(NotificationEvent event) {
        return Mono.defer(() -> {
            NotificationValidator.validate(event);
            NotificationEvent stamped = new NotificationEvent(event.recipients(), event.type(), event.title(),
                    event.body(), event.data(), event.priority(), source, event.ttlSeconds());
            String json = serialize(stamped);
            return redis.opsForStream()
                    .add(StreamRecords.string(Map.of(NotificationTopics.PAYLOAD_FIELD, json))
                            .withStreamKey(NotificationTopics.INBOUND_STREAM))
                    .flatMap(this::trim)
                    .then();
        }).onErrorResume(error -> {
            log.error("Unable to publish notification of type {}", event.type(), error);
            return Mono.empty();
        });
    }

    private Mono<Long> trim(RecordId id) {
        return redis.opsForStream().trim(NotificationTopics.INBOUND_STREAM, MAX_STREAM_LENGTH, true);
    }

    private String serialize(NotificationEvent event) {
        try {
            return mapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Notification cannot be serialised", e);
        }
    }
}
