package dev.varshit.proctor.notifications;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationEvent(
        Set<UUID> recipients,
        String type,
        String title,
        String body,
        Map<String, String> data,
        NotificationPriority priority,
        String source,
        Long ttlSeconds
) {

    public static Builder builder(String type, String title) {
        return new Builder(type, title);
    }

    public static final class Builder {

        private final String type;
        private final String title;
        private Set<UUID> recipients = Set.of();
        private String body;
        private Map<String, String> data = Map.of();
        private NotificationPriority priority = NotificationPriority.normal;
        private Long ttlSeconds;

        private Builder(String type, String title) {
            this.type = type;
            this.title = title;
        }

        public Builder to(Set<UUID> recipients) {
            this.recipients = recipients;
            return this;
        }

        public Builder to(UUID recipient) {
            this.recipients = Set.of(recipient);
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder data(Map<String, String> data) {
            this.data = data;
            return this;
        }

        public Builder priority(NotificationPriority priority) {
            this.priority = priority;
            return this;
        }

        public Builder ttlSeconds(Long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
            return this;
        }

        public NotificationEvent build() {
            return new NotificationEvent(recipients, type, title, body, data, priority, null, ttlSeconds);
        }
    }
}
