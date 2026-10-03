package dev.varshit.proctor.user.service;

import dev.varshit.proctor.common.dto.DepartmentDTO;
import dev.varshit.proctor.common.dto.PermissionDTO;
import dev.varshit.proctor.common.dto.RoleWithPermissionDTO;
import dev.varshit.proctor.common.dto.UserDTO;
import dev.varshit.proctor.common.exception.NotFoundException;
import dev.varshit.proctor.common.exception.UnauthorizedException;
import dev.varshit.proctor.persistence.rls.SecureTransaction;
import dev.varshit.proctor.security.CurrentUser;
import dev.varshit.proctor.user.repository.UserMembershipRepository;
import dev.varshit.proctor.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Function;

@Service
public class UserProfileServiceImpl implements UserProfileService {

    private final UserRepository users;
    private final UserMembershipRepository memberships;
    private final SecureTransaction transaction;

    public UserProfileServiceImpl(UserRepository users, UserMembershipRepository memberships,
                                  SecureTransaction transaction) {
        this.users = users;
        this.memberships = memberships;
        this.transaction = transaction;
    }

    @Override
    public Mono<UserDTO> me() {
        return CurrentUser.require().flatMap(principal -> transaction.mono(() -> users.findById(principal.id()))
                .switchIfEmpty(Mono.error(new UnauthorizedException("Your session is no longer valid. Please sign in again."))));
    }

    @Override
    public Flux<RoleWithPermissionDTO> myRoles() {
        return forMe(this::roles);
    }

    @Override
    public Flux<PermissionDTO> myPermissions() {
        return forMe(this::permissions);
    }

    @Override
    public Flux<DepartmentDTO> myDepartments() {
        return forMe(this::departments);
    }

    @Override
    public Mono<UserDTO> get(UUID id) {
        return transaction.mono(() -> users.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("User " + id + " not found"))));
    }

    @Override
    public Flux<RoleWithPermissionDTO> roles(UUID id) {
        return transaction.flux(() -> memberships.findRoles(id));
    }

    @Override
    public Flux<PermissionDTO> permissions(UUID id) {
        return transaction.flux(() -> memberships.findPermissions(id));
    }

    @Override
    public Flux<DepartmentDTO> departments(UUID id) {
        return transaction.flux(() -> memberships.findDepartments(id));
    }

    private <T> Flux<T> forMe(Function<UUID, Flux<T>> query) {
        return CurrentUser.require().flatMapMany(principal -> query.apply(principal.id()));
    }
}
