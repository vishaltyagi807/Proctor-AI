package dev.varshit.proctor.notifications;

import java.util.UUID;

public final class NotificationTopics {

    public static final String INBOUND_STREAM = "notifications:inbound";
    public static final String USER_CHANNEL_PREFIX = "notifications:user:";
    public static final String PAYLOAD_FIELD = "payload";

    private NotificationTopics() {
    }

    public static String userChannel(UUID userId) {
        return USER_CHANNEL_PREFIX + userId;
    }
}
