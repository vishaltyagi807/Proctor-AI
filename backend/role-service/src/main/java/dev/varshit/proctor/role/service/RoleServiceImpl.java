package dev.varshit.proctor.role.service;

import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.common.dto.PermissionDTO;
import dev.varshit.proctor.common.dto.RoleDTO;
import dev.varshit.proctor.common.dto.RoleWithPermissionDTO;
import dev.varshit.proctor.common.enums.CustomFieldEntity;
import dev.varshit.proctor.common.exception.ForbiddenException;
import dev.varshit.proctor.customfields.CustomFieldContext;
import dev.varshit.proctor.customfields.CustomFieldService;
import dev.varshit.proctor.customfields.WriteMode;
import dev.varshit.proctor.common.exception.NotFoundException;
import dev.varshit.proctor.persistence.rls.SecureTransaction;
import dev.varshit.proctor.persistence.search.PageParams;
import dev.varshit.proctor.role.dto.CreateRoleRequest;
import dev.varshit.proctor.role.dto.PatchRoleRequest;
import dev.varshit.proctor.role.dto.UpdateRoleRequest;
import dev.varshit.proctor.role.repository.RoleRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RoleServiceImpl implements RoleService {

    private final RoleRepository repository;
    private final SecureTransaction transaction;
    private final CustomFieldService customFields;

    public RoleServiceImpl(RoleRepository repository, SecureTransaction transaction, CustomFieldService customFields) {
        this.repository = repository;
        this.transaction = transaction;
        this.customFields = customFields;
    }

    @Override
    public Mono<PageResponse<RoleDTO>> queryRoles(Map<String, String> filters, PageParams page) {
        return transaction.mono(() -> repository.searchRoles(filters, page));
    }

    @Override
    public Mono<PageResponse<PermissionDTO>> queryPermissions(Map<String, String> filters, PageParams page) {
        return transaction.mono(() -> repository.searchPermissions(filters, page));
    }

    @Override
    public Mono<RoleWithPermissionDTO> get(UUID id) {
        return transaction.mono(() -> load(id));
    }

    @Override
    public Mono<RoleWithPermissionDTO> create(CreateRoleRequest request) {
        UUID id = UUID.randomUUID();
        return transaction.mono(() -> repository.insert(id, request.name(), request.description(), request.level(),
                request.revealIdentityOrDefault())
                .then(repository.addPermissions(id, request.permissionsOrEmpty()))
                .then(applyCustomFields(id, request.customFields(), WriteMode.CREATE))
                .then(load(id)));
    }

    @Override
    public Mono<RoleDTO> update(UUID id, UpdateRoleRequest request) {
        return transaction.mono(() -> repository.update(id, request.name(), request.description(), request.level(),
                Boolean.TRUE.equals(request.revealIdentity()))
                .flatMap(rows -> requireChanged(rows, id))
                .then(applyCustomFields(id, request.customFields(), WriteMode.REPLACE))
                .then(loadRole(id)));
    }

    @Override
    public Mono<RoleDTO> patch(UUID id, PatchRoleRequest request) {
        return transaction.mono(() -> repository.patch(id, request)
                .flatMap(rows -> requireChanged(rows, id))
                .then(applyCustomFields(id, request.customFields(), WriteMode.MERGE))
                .then(loadRole(id)));
    }

    @Override
    public Mono<Void> delete(UUID id) {
        return transaction.mono(() -> repository.delete(id).flatMap(rows -> requireChanged(rows, id)));
    }

    @Override
    public Flux<PermissionDTO> permissions(UUID id) {
        return transaction.flux(() -> loadRole(id).thenMany(repository.findPermissions(id)));
    }

    @Override
    public Flux<PermissionDTO> replacePermissions(UUID id, Set<UUID> permissionIds) {
        return transaction.flux(() -> loadRole(id)
                .then(repository.removeAllPermissions(id))
                .then(repository.addPermissions(id, permissionIds))
                .thenMany(repository.findPermissions(id)));
    }

    @Override
    public Flux<PermissionDTO> addPermissions(UUID id, Set<UUID> permissionIds) {
        return transaction.flux(() -> loadRole(id)
                .then(repository.addPermissions(id, permissionIds))
                .thenMany(repository.findPermissions(id)));
    }

    @Override
    public Flux<PermissionDTO> removePermissions(UUID id, Set<UUID> permissionIds) {
        return transaction.flux(() -> loadRole(id)
                .then(repository.removePermissions(id, permissionIds))
                .thenMany(repository.findPermissions(id)));
    }

    @Override
    public Mono<Void> removeAllPermissions(UUID id) {
        return transaction.mono(() -> loadRole(id).then(repository.removeAllPermissions(id)).then());
    }

    private Mono<Void> applyCustomFields(UUID id, Map<String, Object> values, WriteMode mode) {
        return customFields.apply(CustomFieldEntity.roles, id, values, mode, CustomFieldContext.ofRole(id));
    }

    private Mono<RoleWithPermissionDTO> load(UUID id) {
        return repository.findWithPermissions(id).switchIfEmpty(Mono.error(notFound(id)));
    }

    private Mono<RoleDTO> loadRole(UUID id) {
        return repository.findById(id).switchIfEmpty(Mono.error(notFound(id)));
    }

    private Mono<Void> requireChanged(long rows, UUID id) {
        if (rows > 0) {
            return Mono.empty();
        }
        return repository.findById(id)
                .flatMap(existing -> Mono.<Void>error(new ForbiddenException("You are not allowed to modify this role")))
                .switchIfEmpty(Mono.error(notFound(id)));
    }

    private NotFoundException notFound(UUID id) {
        return new NotFoundException("Role " + id + " not found");
    }
}
