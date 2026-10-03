package dev.varshit.proctor.notification.service;

import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.notification.dto.NotificationDTO;
import dev.varshit.proctor.notification.dto.SendNotificationRequest;
import dev.varshit.proctor.persistence.search.PageParams;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

public interface NotificationService {

    Mono<PageResponse<NotificationDTO>> list(Map<String, String> filters, PageParams page);

    Mono<Long> unreadCount();

    Mono<Void> markRead(UUID id);

    Mono<Long> markAllRead();

    Mono<Void> delete(UUID id);

    Flux<ServerSentEvent<String>> stream();

    Mono<dev.varshit.proctor.notification.dto.SendResult> send(SendNotificationRequest request);
}
