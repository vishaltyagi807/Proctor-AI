package dev.varshit.proctor.notification.push;

public record PushResult(Status status, String detail) {

    public enum Status {
        SENT,
        UNREGISTERED,
        FAILED
    }

    public static PushResult sent(String detail) {
        return new PushResult(Status.SENT, detail);
    }

    public static PushResult unregistered(String detail) {
        return new PushResult(Status.UNREGISTERED, detail);
    }

    public static PushResult failed(String detail) {
        return new PushResult(Status.FAILED, detail);
    }
}
