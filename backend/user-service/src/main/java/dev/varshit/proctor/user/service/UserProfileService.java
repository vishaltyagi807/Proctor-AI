package dev.varshit.proctor.user.service;

import dev.varshit.proctor.common.dto.DepartmentDTO;
import dev.varshit.proctor.common.dto.PermissionDTO;
import dev.varshit.proctor.common.dto.RoleWithPermissionDTO;
import dev.varshit.proctor.common.dto.UserDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UserProfileService {

    Mono<UserDTO> me();

    Flux<RoleWithPermissionDTO> myRoles();

    Flux<PermissionDTO> myPermissions();

    Flux<DepartmentDTO> myDepartments();

    Mono<UserDTO> get(UUID id);

    Flux<RoleWithPermissionDTO> roles(UUID id);

    Flux<PermissionDTO> permissions(UUID id);

    Flux<DepartmentDTO> departments(UUID id);
}
