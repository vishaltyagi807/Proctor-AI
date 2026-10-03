package dev.varshit.proctor.user.repository;

import dev.varshit.proctor.common.dto.DepartmentDTO;
import dev.varshit.proctor.common.dto.PermissionDTO;
import dev.varshit.proctor.common.dto.RoleWithPermissionDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

public interface UserMembershipRepository {

    Flux<RoleWithPermissionDTO> findRoles(UUID userId);

    Flux<PermissionDTO> findPermissions(UUID userId);

    Flux<DepartmentDTO> findDepartments(UUID userId);

    Mono<Long> addRoles(UUID userId, Set<UUID> roleIds);

    Mono<Long> retainRoles(UUID userId, Set<UUID> roleIds);

    Mono<Long> addDepartments(UUID userId, Set<UUID> departmentIds);

    Mono<Long> retainDepartments(UUID userId, Set<UUID> departmentIds);
}
