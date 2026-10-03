package dev.varshit.proctor.customfields;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface CustomFieldContextLoader {

    Mono<CustomFieldContext> forUser(UUID userId);

    Mono<CustomFieldContext> forComplaint(UUID complaintId);
}
