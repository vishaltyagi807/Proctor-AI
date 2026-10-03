package dev.varshit.proctor.notification.repository;

import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.notification.dto.NotificationDTO;
import dev.varshit.proctor.persistence.search.PageParams;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

public interface NotificationRepository {

    Mono<PageResponse<NotificationDTO>> search(Map<String, String> filters, PageParams page);

    Mono<Long> unreadCount();

    Mono<Long> markRead(UUID id);

    Mono<Long> markAllRead();

    Mono<Long> delete(UUID id);

    Mono<Boolean> can(String entity, String action);

    Mono<java.util.List<UUID>> resolveRecipients(java.util.Set<UUID> users, java.util.Set<UUID> roles,
                                                java.util.Set<UUID> departments);
}
