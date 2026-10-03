package dev.varshit.proctor.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.InetAddress;
import java.net.UnknownHostException;

@ConfigurationProperties(prefix = "app.notifications")
public record NotificationProperties(
        String secretKey,
        String consumerName,
        int batchSize,
        int retentionDays,
        Fcm fcm
) {

    public NotificationProperties {
        if (secretKey == null || secretKey.length() < 32) {
            throw new IllegalStateException("NOTIFICATION_SECRET_KEY must be at least 32 characters");
        }
        consumerName = consumerName == null || consumerName.isBlank() ? hostname() : consumerName;
        batchSize = batchSize <= 0 ? 20 : batchSize;
        retentionDays = retentionDays <= 0 ? 90 : retentionDays;
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "notification-consumer";
        }
    }

    public record Fcm(String tokenUrl, String sendUrl) {
    }
}
