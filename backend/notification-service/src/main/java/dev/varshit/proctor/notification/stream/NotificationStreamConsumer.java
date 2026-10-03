package dev.varshit.proctor.notification.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.varshit.proctor.notification.config.NotificationProperties;
import dev.varshit.proctor.notification.service.NotificationDispatcher;
import dev.varshit.proctor.notifications.NotificationEvent;
import dev.varshit.proctor.notifications.NotificationTopics;
import dev.varshit.proctor.notifications.NotificationValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.stream.StreamReceiver;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class NotificationStreamConsumer implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(NotificationStreamConsumer.class);
    private static final String GROUP = "notification-service";

    private final ReactiveStringRedisTemplate redis;
    private final ReactiveRedisConnectionFactory factory;
    private final NotificationDispatcher dispatcher;
    private final NotificationProperties properties;
    private final ObjectMapper mapper;
    private Disposable subscription;

    public NotificationStreamConsumer(ReactiveStringRedisTemplate redis, ReactiveRedisConnectionFactory factory,
                                      NotificationDispatcher dispatcher, NotificationProperties properties,
                                      ObjectMapper mapper) {
        this.redis = redis;
        this.factory = factory;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Consumer consumer = Consumer.from(GROUP, properties.consumerName());
        subscription = Flux.defer(() -> ensureGroup()
                        .then(drainPending(consumer))
                        .thenMany(consume(consumer)))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(signal -> log.warn("Notification stream restarting: {}",
                                signal.failure().getMessage())))
                .subscribe(unused -> {
                }, error -> log.error("Notification consumer stopped", error));
        log.info("Notification consumer '{}' started on stream {}", properties.consumerName(),
                NotificationTopics.INBOUND_STREAM);
    }

    @Override
    public void destroy() {
        if (subscription != null) {
            subscription.dispose();
        }
    }

    private Mono<Void> ensureGroup() {
        ByteBuffer key = ByteBuffer.wrap(NotificationTopics.INBOUND_STREAM.getBytes(StandardCharsets.UTF_8));
        return factory.getReactiveConnection().streamCommands()
                .xGroupCreate(key, GROUP, ReadOffset.from("0"), true)
                .then()
                .onErrorResume(this::isBusyGroup, error -> Mono.empty());
    }

    private boolean isBusyGroup(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (String.valueOf(current.getMessage()).contains("BUSYGROUP")) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> drainPending(Consumer consumer) {
        return redis.opsForStream()
                .read(consumer, StreamReadOptions.empty().count(properties.batchSize()),
                        StreamOffset.create(NotificationTopics.INBOUND_STREAM, ReadOffset.from("0")))
                .concatMap(record -> handle(record.getId(), String.valueOf(record.getValue().get(NotificationTopics.PAYLOAD_FIELD))))
                .count()
                .flatMap(count -> count > 0 ? drainPending(consumer) : Mono.empty());
    }

    private Flux<Void> consume(Consumer consumer) {
        StreamReceiver<String, MapRecord<String, String, String>> receiver = StreamReceiver.create(factory,
                StreamReceiver.StreamReceiverOptions.builder()
                        .pollTimeout(Duration.ofSeconds(2))
                        .batchSize(properties.batchSize())
                        .build());
        return receiver.receive(consumer, StreamOffset.create(NotificationTopics.INBOUND_STREAM, ReadOffset.lastConsumed()))
                .concatMap(record -> handle(record.getId(), record.getValue().get(NotificationTopics.PAYLOAD_FIELD)));
    }

    private Mono<Void> handle(RecordId id, String payload) {
        return process(payload)
                .onErrorResume(error -> {
                    log.error("Dropping notification {}: {}", id, error.getMessage());
                    return Mono.empty();
                })
                .then(Mono.defer(() -> redis.opsForStream().acknowledge(NotificationTopics.INBOUND_STREAM, GROUP, id)))
                .then();
    }

    private Mono<Void> process(String payload) {
        return Mono.fromCallable(() -> {
            NotificationEvent event = mapper.readValue(payload, NotificationEvent.class);
            NotificationValidator.validate(event);
            return event;
        }).flatMap(dispatcher::dispatch);
    }
}
