package dev.varshit.proctor.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class InstanceReporter implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(InstanceReporter.class);

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Environment environment;
    private final ObjectProvider<MeterRegistry> registry;
    private final AtomicBoolean failing = new AtomicBoolean(false);

    private volatile InstanceSampler sampler;
    private volatile Disposable task;

    public InstanceReporter(ReactiveStringRedisTemplate redis, ObjectMapper mapper, Environment environment,
                            ObjectProvider<MeterRegistry> registry) {
        this.redis = redis;
        this.mapper = mapper;
        this.environment = environment;
        this.registry = registry;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (task != null) {
            return;
        }
        String service = environment.getProperty("spring.application.name", "unknown").toLowerCase(Locale.ROOT);
        int port = environment.getProperty("local.server.port", Integer.class,
                environment.getProperty("server.port", Integer.class, 0));
        String host = "localhost";
        String address = "127.0.0.1";
        try {
            InetAddress local = InetAddress.getLocalHost();
            host = local.getHostName();
            address = local.getHostAddress();
        } catch (Exception ignored) {
            log.debug("Falling back to localhost for monitoring identity");
        }
        sampler = new InstanceSampler(service, host, address, port, registry);
        String key = MonitoringKeys.instanceKey(sampler.instanceId());
        task = Flux.interval(Duration.ZERO, MonitoringKeys.REPORT_INTERVAL, Schedulers.boundedElastic())
                .onBackpressureDrop()
                .concatMap(tick -> report(key))
                .subscribe();
    }

    private Mono<Boolean> report(String key) {
        return Mono.fromCallable(() -> mapper.writeValueAsString(sampler.sample()))
                .flatMap(json -> redis.opsForValue().set(key, json, MonitoringKeys.REPORT_TTL))
                .doOnNext(ok -> {
                    if (failing.compareAndSet(true, false)) {
                        log.info("Resource reporting to Redis recovered");
                    }
                })
                .onErrorResume(error -> {
                    if (failing.compareAndSet(false, true)) {
                        log.warn("Resource reporting to Redis failed: {}", error.getMessage());
                    }
                    return Mono.empty();
                });
    }

    @Override
    public void destroy() {
        Disposable running = task;
        if (running != null) {
            running.dispose();
        }
        InstanceSampler current = sampler;
        if (current == null) {
            return;
        }
        try {
            redis.delete(MonitoringKeys.instanceKey(current.instanceId())).block(Duration.ofSeconds(2));
        } catch (RuntimeException ignored) {
            log.debug("Could not remove monitoring key on shutdown");
        }
    }
}
