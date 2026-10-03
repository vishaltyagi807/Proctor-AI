package dev.varshit.proctor.notification.dto;

public record DeliveryStatsDTO(long realtimeSent, long fcmSent, long fcmFailed, long skipped, long activeDevices,
                               long notificationsLast24h) {
}
