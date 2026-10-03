package dev.varshit.proctor.notifications.progress;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;

@AutoConfiguration(after = RedisReactiveAutoConfiguration.class)
public class ProgressAutoConfiguration {

    @Bean(destroyMethod = "destroy")
    @ConditionalOnBean(ReactiveRedisConnectionFactory.class)
    @ConditionalOnMissingBean(ReactiveRedisMessageListenerContainer.class)
    public ReactiveRedisMessageListenerContainer progressRedisMessageListenerContainer(ReactiveRedisConnectionFactory factory) {
        return new ReactiveRedisMessageListenerContainer(factory);
    }

    @Bean
    @ConditionalOnBean(ReactiveStringRedisTemplate.class)
    @ConditionalOnMissingBean(ProgressBroker.class)
    public ProgressBroker progressBroker(ReactiveStringRedisTemplate redis, ReactiveRedisMessageListenerContainer container,
                                         ObjectMapper mapper) {
        return new RedisProgressBroker(redis, container, mapper);
    }
}
