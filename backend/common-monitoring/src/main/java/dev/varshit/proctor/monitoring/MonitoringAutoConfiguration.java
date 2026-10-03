package dev.varshit.proctor.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@AutoConfiguration(after = {RedisReactiveAutoConfiguration.class, JacksonAutoConfiguration.class})
@ConditionalOnProperty(prefix = "app.monitoring", name = "enabled", matchIfMissing = true)
public class MonitoringAutoConfiguration {

    @Bean
    @ConditionalOnBean(ReactiveStringRedisTemplate.class)
    @ConditionalOnMissingBean(InstanceReporter.class)
    public InstanceReporter instanceReporter(ReactiveStringRedisTemplate redis, ObjectProvider<ObjectMapper> mapper,
                                             Environment environment, ObjectProvider<MeterRegistry> registry) {
        return new InstanceReporter(redis, mapper.getIfAvailable(ObjectMapper::new), environment, registry);
    }
}
