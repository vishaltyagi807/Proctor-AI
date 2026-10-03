package dev.varshit.proctor.complaint.service;

import dev.varshit.proctor.common.enums.ComplaintStatus;
import dev.varshit.proctor.complaint.dto.ComplaintParties;
import dev.varshit.proctor.complaint.repository.ComplaintRepository;
import dev.varshit.proctor.notifications.NotificationEvent;
import dev.varshit.proctor.notifications.NotificationPriority;
import dev.varshit.proctor.notifications.NotificationPublisher;
import dev.varshit.proctor.persistence.rls.SecureTransaction;
import dev.varshit.proctor.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Component
public class EventComplaintNotifier implements ComplaintNotifier {

    private static final Logger log = LoggerFactory.getLogger(EventComplaintNotifier.class);

    private final ComplaintRepository complaints;
    private final SecureTransaction transaction;
    private final ObjectProvider<NotificationPublisher> publisher;

    public EventComplaintNotifier(ComplaintRepository complaints, SecureTransaction transaction,
                                  ObjectProvider<NotificationPublisher> publisher) {
        this.complaints = complaints;
        this.transaction = transaction;
        this.publisher = publisher;
    }

    @Override
    public Mono<Void> created(UUID complaintId) {
        return notify(complaintId, parties -> {
            Set<UUID> recipients = new HashSet<>();
            if (parties.raisedBy() != null && !parties.raisedBy().equals(parties.studentId())) {
                recipients.add(parties.studentId());
            }
            recipients.addAll(parties.subjectIdsOrEmpty());
            if (parties.assignedTo() != null) {
                recipients.add(parties.assignedTo());
            }
            return recipients;
        }, "complaint.created", "A complaint was filed", parties -> "Complaint '" + parties.title() + "' was filed.",
                NotificationPriority.normal);
    }

    @Override
    public Mono<Void> statusChanged(UUID complaintId, ComplaintStatus status) {
        return notify(complaintId, this::everyone, "complaint.status_changed", "Complaint status updated",
                parties -> "Complaint '" + parties.title() + "' is now " + status.name() + ".",
                status == ComplaintStatus.resolved || status == ComplaintStatus.rejected
                        ? NotificationPriority.high : NotificationPriority.normal);
    }

    @Override
    public Mono<Void> assigned(UUID complaintId) {
        return notify(complaintId, parties -> parties.assignedTo() == null ? Set.of() : Set.of(parties.assignedTo()),
                "complaint.assigned", "A complaint was assigned to you",
                parties -> "You were assigned complaint '" + parties.title() + "'.", NotificationPriority.normal);
    }

    @Override
    public Mono<Void> commented(UUID complaintId, boolean internal) {
        return notify(complaintId,
                parties -> internal
                        ? (parties.assignedTo() == null ? Set.<UUID>of() : Set.of(parties.assignedTo()))
                        : everyone(parties),
                "complaint.comment", "New comment on a complaint",
                parties -> "There is a new comment on complaint '" + parties.title() + "'.", NotificationPriority.normal);
    }

    private Set<UUID> everyone(ComplaintParties parties) {
        Set<UUID> recipients = new HashSet<>();
        if (parties.studentId() != null) {
            recipients.add(parties.studentId());
        }
        recipients.addAll(parties.subjectIdsOrEmpty());
        if (parties.raisedBy() != null) {
            recipients.add(parties.raisedBy());
        }
        if (parties.assignedTo() != null) {
            recipients.add(parties.assignedTo());
        }
        return recipients;
    }

    private Mono<Void> notify(UUID complaintId, Function<ComplaintParties, Set<UUID>> recipients, String type,
                              String title, Function<ComplaintParties, String> body, NotificationPriority priority) {
        NotificationPublisher target = publisher.getIfAvailable();
        if (target == null) {
            return Mono.empty();
        }
        return CurrentUser.require().flatMap(actor -> transaction.monoAs(actor, () -> complaints.parties(complaintId))
                        .flatMap(parties -> {
                            Set<UUID> to = new HashSet<>(recipients.apply(parties));
                            to.remove(actor.id());
                            if (to.isEmpty()) {
                                return Mono.<Void>empty();
                            }
                            return target.publish(NotificationEvent.builder(type, title)
                                    .to(to)
                                    .body(body.apply(parties))
                                    .data(Map.of("complaintId", complaintId.toString(), "route", "/complaints/" + complaintId))
                                    .priority(priority)
                                    .build());
                        }))
                .onErrorResume(error -> {
                    log.warn("Complaint notification skipped: {}", error.getMessage());
                    return Mono.empty();
                });
    }
}
