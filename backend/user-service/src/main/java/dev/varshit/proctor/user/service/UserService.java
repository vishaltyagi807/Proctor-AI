package dev.varshit.proctor.user.service;

import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.common.dto.UserInfoDTO;
import dev.varshit.proctor.persistence.search.PageParams;
import dev.varshit.proctor.security.UserPrincipal;
import dev.varshit.proctor.user.dto.CreateUserRequest;
import dev.varshit.proctor.user.dto.PatchUserRequest;
import dev.varshit.proctor.user.dto.UpdateUserRequest;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface UserService {

    Mono<PageResponse<UserInfoDTO>> query(Map<String, String> filters, PageParams page);

    Mono<UserInfoDTO> create(CreateUserRequest request);

    Mono<UserInfoDTO> createAs(UserPrincipal actor, CreateUserRequest request);

    Mono<List<UserInfoDTO>> createBulk(List<CreateUserRequest> requests);

    Mono<UserInfoDTO> update(UUID id, UpdateUserRequest request);

    Mono<UserInfoDTO> patch(UUID id, PatchUserRequest request);

    Mono<Void> delete(Set<UUID> ids);
}
