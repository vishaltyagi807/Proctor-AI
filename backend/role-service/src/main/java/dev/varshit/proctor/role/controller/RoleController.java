package dev.varshit.proctor.role.controller;

import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.common.dto.PermissionDTO;
import dev.varshit.proctor.common.dto.RoleDTO;
import dev.varshit.proctor.common.dto.RoleWithPermissionDTO;
import dev.varshit.proctor.persistence.search.PageParams;
import dev.varshit.proctor.role.dto.CreateRoleRequest;
import dev.varshit.proctor.role.dto.PatchRoleRequest;
import dev.varshit.proctor.role.dto.UpdateRoleRequest;
import dev.varshit.proctor.role.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/roles")
public class RoleController {

    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    @PostMapping
    public Mono<RoleWithPermissionDTO> create(@Valid @RequestBody CreateRoleRequest request) {
        return service.create(request);
    }

    @GetMapping
    public Mono<PageResponse<RoleDTO>> query(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam Map<String, String> params
    ) {
        return service.queryRoles(params, PageParams.of(page, size, sortBy, direction));
    }

    @GetMapping("/permissions")
    public Mono<PageResponse<PermissionDTO>> queryPermissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "entity") String sortBy,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam Map<String, String> params
    ) {
        return service.queryPermissions(params, PageParams.of(page, size, sortBy, direction));
    }

    @GetMapping("/{id}")
    public Mono<RoleWithPermissionDTO> get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public Mono<RoleDTO> update(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}")
    public Mono<RoleDTO> patch(@PathVariable UUID id, @Valid @RequestBody PatchRoleRequest request) {
        return service.patch(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable UUID id) {
        return service.delete(id);
    }

    @GetMapping("/{id}/permissions")
    public Flux<PermissionDTO> permissions(@PathVariable UUID id) {
        return service.permissions(id);
    }

    @PutMapping("/{id}/permissions")
    public Flux<PermissionDTO> replacePermissions(@PathVariable UUID id, @RequestBody Set<UUID> permissionIds) {
        return service.replacePermissions(id, permissionIds);
    }

    @PatchMapping("/{id}/permissions")
    public Flux<PermissionDTO> addPermissions(@PathVariable UUID id, @RequestBody Set<UUID> permissionIds) {
        return service.addPermissions(id, permissionIds);
    }

    @DeleteMapping("/{id}/permissions")
    public Flux<PermissionDTO> removePermissions(@PathVariable UUID id, @RequestBody Set<UUID> permissionIds) {
        return service.removePermissions(id, permissionIds);
    }

    @DeleteMapping("/{id}/permissions/all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> removeAllPermissions(@PathVariable UUID id) {
        return service.removeAllPermissions(id);
    }
}
