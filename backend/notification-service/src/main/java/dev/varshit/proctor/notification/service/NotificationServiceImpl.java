package dev.varshit.proctor.notification.service;

import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.common.exception.BadRequestException;
import dev.varshit.proctor.common.exception.NotFoundException;
import dev.varshit.proctor.notification.dto.NotificationDTO;
import dev.varshit.proctor.notification.dto.SendNotificationRequest;
import dev.varshit.proctor.notification.dto.SendResult;
import dev.varshit.proctor.notification.realtime.RealtimeBroker;
import dev.varshit.proctor.notification.repository.NotificationRepository;
import dev.varshit.proctor.notifications.NotificationEvent;
import dev.varshit.proctor.notifications.NotificationPriority;
import dev.varshit.proctor.notifications.NotificationPublisher;
import dev.varshit.proctor.persistence.rls.SecureTransaction;
import dev.varshit.proctor.persistence.search.PageParams;
import dev.varshit.proctor.security.CurrentUser;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final int CHUNK_SIZE = 500;
    private static final int MAX_RECIPIENTS = 20_000;

    private final NotificationRepository repository;
    private final SecureTransaction transaction;
    private final RealtimeBroker broker;
    private final NotificationPublisher publisher;

    public NotificationServiceImpl(NotificationRepository repository, SecureTransaction transaction,
                                   RealtimeBroker broker, NotificationPublisher publisher) {
        this.repository = repository;
        this.transaction = transaction;
        this.broker = broker;
        this.publisher = publisher;
    }

    @Override
    public Mono<PageResponse<NotificationDTO>> list(Map<String, String> filters, PageParams page) {
        Map<String, String> effective = new HashMap<>(filters);
        if ("true".equalsIgnoreCase(effective.remove("unread"))) {
            effective.put("readAt", "is.null");
        }
        return transaction.mono(() -> repository.search(effective, page));
    }

    @Override
    public Mono<Long> unreadCount() {
        return transaction.mono(repository::unreadCount);
    }

    @Override
    public Mono<Void> markRead(UUID id) {
        return transaction.mono(() -> repository.markRead(id).flatMap(rows -> rows == 0
                ? Mono.<Void>error(new NotFoundException("Notification not found")) : Mono.empty()));
    }

    @Override
    public Mono<Long> markAllRead() {
        return transaction.mono(repository::markAllRead);
    }

    @Override
    public Mono<Void> delete(UUID id) {
        return transaction.mono(() -> repository.delete(id).flatMap(rows -> rows == 0
                ? Mono.<Void>error(new NotFoundException("Notification not found")) : Mono.empty()));
    }

    @Override
    public Flux<ServerSentEvent<String>> stream() {
        return CurrentUser.require().flatMapMany(principal -> broker.subscribe(principal.id()));
    }

    @Override
    public Mono<SendResult> send(SendNotificationRequest request) {
        if (!request.hasTargets()) {
            return Mono.error(new BadRequestException("Provide at least one of userIds, roleIds or departmentIds"));
        }
        return CurrentUser.require().flatMap(sender -> transaction.monoAs(sender, () -> repository
                        .resolveRecipients(request.users(), request.roles(), request.departments()))
                .flatMap(resolved -> {
                    Set<UUID> recipients = new LinkedHashSet<>(resolved);
                    if (!Boolean.TRUE.equals(request.includeSender()) && !request.users().contains(sender.id())) {
                        recipients.remove(sender.id());
                    }
                    if (recipients.isEmpty()) {
                        return Mono.<SendResult>error(new BadRequestException("No eligible recipients"));
                    }
                    if (recipients.size() > MAX_RECIPIENTS) {
                        return Mono.<SendResult>error(new BadRequestException(
                                "At most " + MAX_RECIPIENTS + " recipients per message"));
                    }
                    return Flux.fromIterable(chunks(recipients))
                            .concatMap(chunk -> publisher.publish(event(request, chunk)))
                            .then(Mono.just(new SendResult("Notification queued", recipients.size())));
                }));
    }

    private NotificationEvent event(SendNotificationRequest request, Set<UUID> recipients) {
        return NotificationEvent.builder(request.type(), request.title())
                .to(recipients)
                .body(request.body())
                .data(request.data() == null ? Map.of() : request.data())
                .priority(request.priority() == null ? NotificationPriority.normal : request.priority())
                .build();
    }

    private List<Set<UUID>> chunks(Set<UUID> recipients) {
        List<Set<UUID>> result = new ArrayList<>();
        Set<UUID> current = new LinkedHashSet<>();
        for (UUID id : recipients) {
            current.add(id);
            if (current.size() == CHUNK_SIZE) {
                result.add(current);
                current = new LinkedHashSet<>();
            }
        }
        if (!current.isEmpty()) {
            result.add(current);
        }
        return result;
    }
}
