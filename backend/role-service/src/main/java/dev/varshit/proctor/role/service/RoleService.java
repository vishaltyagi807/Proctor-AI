package dev.varshit.proctor.role.service;

import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.common.dto.PermissionDTO;
import dev.varshit.proctor.common.dto.RoleDTO;
import dev.varshit.proctor.common.dto.RoleWithPermissionDTO;
import dev.varshit.proctor.persistence.search.PageParams;
import dev.varshit.proctor.role.dto.CreateRoleRequest;
import dev.varshit.proctor.role.dto.PatchRoleRequest;
import dev.varshit.proctor.role.dto.UpdateRoleRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface RoleService {

    Mono<PageResponse<RoleDTO>> queryRoles(Map<String, String> filters, PageParams page);

    Mono<PageResponse<PermissionDTO>> queryPermissions(Map<String, String> filters, PageParams page);

    Mono<RoleWithPermissionDTO> get(UUID id);

    Mono<RoleWithPermissionDTO> create(CreateRoleRequest request);

    Mono<RoleDTO> update(UUID id, UpdateRoleRequest request);

    Mono<RoleDTO> patch(UUID id, PatchRoleRequest request);

    Mono<Void> delete(UUID id);

    Flux<PermissionDTO> permissions(UUID id);

    Flux<PermissionDTO> replacePermissions(UUID id, Set<UUID> permissionIds);

    Flux<PermissionDTO> addPermissions(UUID id, Set<UUID> permissionIds);

    Flux<PermissionDTO> removePermissions(UUID id, Set<UUID> permissionIds);

    Mono<Void> removeAllPermissions(UUID id);
}
