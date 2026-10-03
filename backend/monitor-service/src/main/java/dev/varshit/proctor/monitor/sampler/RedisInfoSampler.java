package dev.varshit.proctor.monitor.sampler;

import dev.varshit.proctor.monitor.dto.RedisStats;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Properties;

@Component
public class RedisInfoSampler {

    private final ReactiveStringRedisTemplate redis;

    public RedisInfoSampler(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<RedisStats> sample() {
        return redis.execute(connection -> connection.serverCommands().info()).next().map(this::toStats);
    }

    private RedisStats toStats(Properties info) {
        long hits = number(info, "keyspace_hits");
        long misses = number(info, "keyspace_misses");
        long keys = 0;
        long expiring = 0;
        for (String name : info.stringPropertyNames()) {
            if (!name.matches("db\\d+")) {
                continue;
            }
            for (String part : info.getProperty(name).split(",")) {
                String[] pair = part.split("=");
                if (pair.length == 2 && "keys".equals(pair[0])) {
                    keys += parse(pair[1]);
                } else if (pair.length == 2 && "expires".equals(pair[0])) {
                    expiring += parse(pair[1]);
                }
            }
        }
        return new RedisStats(
                info.getProperty("redis_version"),
                info.getProperty("role"),
                number(info, "uptime_in_seconds"),
                number(info, "connected_clients"),
                number(info, "blocked_clients"),
                number(info, "used_memory"),
                number(info, "used_memory_peak"),
                number(info, "maxmemory"),
                number(info, "instantaneous_ops_per_sec"),
                hits + misses == 0 ? 0 : (double) hits / (hits + misses),
                keys,
                expiring,
                number(info, "evicted_keys"),
                decimal(info, "instantaneous_input_kbps"),
                decimal(info, "instantaneous_output_kbps"));
    }

    private static long number(Properties info, String key) {
        return parse(info.getProperty(key));
    }

    private static long parse(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    private static double decimal(Properties info, String key) {
        String value = info.getProperty(key);
        if (value == null) {
            return 0;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) ? parsed : 0;
        } catch (NumberFormatException error) {
            return 0;
        }
    }
}
