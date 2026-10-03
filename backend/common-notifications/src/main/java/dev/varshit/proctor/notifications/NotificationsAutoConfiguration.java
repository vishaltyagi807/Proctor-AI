package dev.varshit.proctor.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@AutoConfiguration(after = RedisReactiveAutoConfiguration.class)
public class NotificationsAutoConfiguration {

    @Bean
    @ConditionalOnBean(ReactiveStringRedisTemplate.class)
    @ConditionalOnMissingBean(NotificationPublisher.class)
    public NotificationPublisher notificationPublisher(
            ReactiveStringRedisTemplate redis,
            ObjectMapper mapper,
            @Value("${spring.application.name:unknown}") String source
    ) {
        return new RedisNotificationPublisher(redis, mapper, source.toLowerCase());
    }
}
