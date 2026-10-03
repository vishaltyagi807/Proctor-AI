package dev.varshit.proctor.notifications;

import dev.varshit.proctor.common.exception.BadRequestException;

public final class NotificationValidator {

    public static final int MAX_RECIPIENTS = 1000;
    public static final int MAX_TITLE = 200;
    public static final int MAX_BODY = 2000;
    public static final int MAX_DATA_ENTRIES = 20;
    public static final int MAX_DATA_VALUE = 1000;

    private NotificationValidator() {
    }

    public static void validate(NotificationEvent event) {
        if (event.recipients() == null || event.recipients().isEmpty()) {
            throw new BadRequestException("At least one recipient is required");
        }
        if (event.recipients().size() > MAX_RECIPIENTS) {
            throw new BadRequestException("At most " + MAX_RECIPIENTS + " recipients are allowed");
        }
        if (event.type() == null || event.type().isBlank() || event.type().length() > 100) {
            throw new BadRequestException("type is required (max 100 characters)");
        }
        if (event.title() == null || event.title().isBlank() || event.title().length() > MAX_TITLE) {
            throw new BadRequestException("title is required (max " + MAX_TITLE + " characters)");
        }
        if (event.body() != null && event.body().length() > MAX_BODY) {
            throw new BadRequestException("body is limited to " + MAX_BODY + " characters");
        }
        if (event.data() != null) {
            if (event.data().size() > MAX_DATA_ENTRIES) {
                throw new BadRequestException("data is limited to " + MAX_DATA_ENTRIES + " entries");
            }
            event.data().forEach((key, value) -> {
                if (key == null || key.isBlank() || key.length() > 100 || (value != null && value.length() > MAX_DATA_VALUE)) {
                    throw new BadRequestException("data keys and values exceed the allowed size");
                }
            });
        }
    }
}
