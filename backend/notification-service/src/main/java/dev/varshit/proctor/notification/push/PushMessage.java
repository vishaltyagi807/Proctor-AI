package dev.varshit.proctor.notification.push;

import java.util.Map;

public record PushMessage(String title, String body, Map<String, String> data, boolean highPriority) {
}
