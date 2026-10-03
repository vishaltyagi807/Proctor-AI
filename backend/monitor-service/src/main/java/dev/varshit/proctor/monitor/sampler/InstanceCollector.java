package dev.varshit.proctor.monitor.sampler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.varshit.proctor.monitoring.InstanceSnapshot;
import dev.varshit.proctor.monitoring.MonitoringKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class InstanceCollector {

    public record Registration(String service, String host, int port) {
    }

    private static final Logger log = LoggerFactory.getLogger(InstanceCollector.class);

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final ReactiveDiscoveryClient discovery;

    public InstanceCollector(ReactiveStringRedisTemplate redis, ObjectMapper mapper, ReactiveDiscoveryClient discovery) {
        this.redis = redis;
        this.mapper = mapper;
        this.discovery = discovery;
    }

    public Mono<List<InstanceSnapshot>> snapshots() {
        return redis.scan(ScanOptions.scanOptions().match(MonitoringKeys.INSTANCE_PREFIX + "*").count(200).build())
                .collectList()
                .flatMap(keys -> keys.isEmpty() ? Mono.just(List.<String>of()) : redis.opsForValue().multiGet(keys))
                .map(values -> values.stream().filter(Objects::nonNull).map(this::parse).filter(Objects::nonNull).toList());
    }

    public Mono<List<Registration>> registrations() {
        return discovery.getServices()
                .flatMap(discovery::getInstances)
                .map(instance -> new Registration(instance.getServiceId().toLowerCase(Locale.ROOT), instance.getHost(),
                        instance.getPort()))
                .collectList();
    }

    private InstanceSnapshot parse(String json) {
        try {
            return mapper.readValue(json, InstanceSnapshot.class);
        } catch (Exception error) {
            log.debug("Skipping unreadable instance snapshot: {}", error.getMessage());
            return null;
        }
    }
}
