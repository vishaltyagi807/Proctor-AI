package dev.varshit.proctor.notification.dto;

public record TestPushResult(boolean credentialsValid, int devicesTried, int sent, int failed, String detail) {
}
