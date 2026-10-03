package dev.varshit.proctor.role.repository;

import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.common.dto.PermissionDTO;
import dev.varshit.proctor.common.dto.RoleDTO;
import dev.varshit.proctor.common.dto.RoleWithPermissionDTO;
import dev.varshit.proctor.persistence.search.PageParams;
import dev.varshit.proctor.role.dto.PatchRoleRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface RoleRepository {

    Mono<PageResponse<RoleDTO>> searchRoles(Map<String, String> filters, PageParams page);

    Mono<PageResponse<PermissionDTO>> searchPermissions(Map<String, String> filters, PageParams page);

    Mono<RoleWithPermissionDTO> findWithPermissions(UUID id);

    Mono<RoleDTO> findById(UUID id);

    Flux<PermissionDTO> findPermissions(UUID roleId);

    Mono<Long> insert(UUID id, String name, String description, int level, boolean revealIdentity);

    Mono<Long> update(UUID id, String name, String description, int level, boolean revealIdentity);

    Mono<Long> patch(UUID id, PatchRoleRequest request);

    Mono<Long> delete(UUID id);

    Mono<Long> addPermissions(UUID roleId, Set<UUID> permissionIds);

    Mono<Long> removePermissions(UUID roleId, Set<UUID> permissionIds);

    Mono<Long> removeAllPermissions(UUID roleId);
}
