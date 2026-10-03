package dev.varshit.proctor.user.repository;

import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.common.dto.UserDTO;
import dev.varshit.proctor.common.dto.UserInfoDTO;
import dev.varshit.proctor.persistence.search.PageParams;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface UserRepository {

    Mono<PageResponse<UserInfoDTO>> search(Map<String, String> filters, PageParams page);

    Mono<UserInfoDTO> findInfoById(UUID id);

    Mono<UserDTO> findById(UUID id);

    Flux<String> findExistingEmails(Collection<String> emails);

    Mono<Long> insert(UUID id, String email, String name, String passwordHash, boolean enabled, boolean verified);

    Mono<Long> update(UUID id, String email, String name, String passwordHash, boolean enabled, boolean verified);

    Mono<Long> patch(UUID id, String email, String name, String passwordHash, Boolean enabled, Boolean verified);

    Mono<Long> deleteAll(Set<UUID> ids);
}
