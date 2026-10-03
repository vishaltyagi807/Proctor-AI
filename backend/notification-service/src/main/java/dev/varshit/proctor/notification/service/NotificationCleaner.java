package dev.varshit.proctor.notification.service;

import dev.varshit.proctor.notification.config.NotificationProperties;
import dev.varshit.proctor.notification.repository.SystemNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationCleaner {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleaner.class);

    private final SystemNotificationRepository repository;
    private final NotificationProperties properties;

    public NotificationCleaner(SystemNotificationRepository repository, NotificationProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "PT6H", initialDelayString = "PT5M")
    public void purge() {
        repository.purgeOld(properties.retentionDays()).subscribe(
                count -> {
                    if (count > 0) {
                        log.info("Removed {} old notifications", count);
                    }
                },
                error -> log.error("Notification cleanup failed", error));
    }
}
