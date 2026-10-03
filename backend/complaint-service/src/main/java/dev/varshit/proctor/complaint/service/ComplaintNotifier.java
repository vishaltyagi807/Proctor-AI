package dev.varshit.proctor.complaint.service;

import dev.varshit.proctor.common.enums.ComplaintStatus;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ComplaintNotifier {

    Mono<Void> created(UUID complaintId);

    Mono<Void> statusChanged(UUID complaintId, ComplaintStatus status);

    Mono<Void> assigned(UUID complaintId);

    Mono<Void> commented(UUID complaintId, boolean internal);
}
