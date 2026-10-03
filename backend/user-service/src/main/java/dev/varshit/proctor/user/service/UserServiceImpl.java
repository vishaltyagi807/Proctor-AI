package dev.varshit.proctor.user.service;

import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.common.dto.UserInfoDTO;
import dev.varshit.proctor.common.enums.CustomFieldEntity;
import dev.varshit.proctor.common.exception.BadRequestException;
import dev.varshit.proctor.customfields.CustomFieldContextLoader;
import dev.varshit.proctor.customfields.CustomFieldService;
import dev.varshit.proctor.customfields.WriteMode;
import dev.varshit.proctor.common.exception.ConflictException;
import dev.varshit.proctor.common.exception.ForbiddenException;
import dev.varshit.proctor.common.exception.NotFoundException;
import dev.varshit.proctor.persistence.rls.SecureTransaction;
import dev.varshit.proctor.persistence.search.PageParams;
import dev.varshit.proctor.security.UserPrincipal;
import dev.varshit.proctor.security.password.PasswordHasher;
import dev.varshit.proctor.user.dto.CreateUserRequest;
import dev.varshit.proctor.user.dto.PatchUserRequest;
import dev.varshit.proctor.user.dto.UpdateUserRequest;
import dev.varshit.proctor.user.repository.UserMembershipRepository;
import dev.varshit.proctor.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository users;
    private final UserMembershipRepository memberships;
    private final SecureTransaction transaction;
    private final PasswordHasher hasher;
    private final CustomFieldService customFields;
    private final CustomFieldContextLoader contexts;

    public UserServiceImpl(
            UserRepository users,
            UserMembershipRepository memberships,
            SecureTransaction transaction,
            PasswordHasher hasher,
            CustomFieldService customFields,
            CustomFieldContextLoader contexts
    ) {
        this.users = users;
        this.memberships = memberships;
        this.transaction = transaction;
        this.hasher = hasher;
        this.customFields = customFields;
        this.contexts = contexts;
    }

    @Override
    public Mono<PageResponse<UserInfoDTO>> query(Map<String, String> filters, PageParams page) {
        return transaction.mono(() -> users.search(filters, page));
    }

    @Override
    public Mono<UserInfoDTO> create(CreateUserRequest request) {
        return transaction.mono(() -> createUser(request));
    }

    @Override
    public Mono<UserInfoDTO> createAs(UserPrincipal actor, CreateUserRequest request) {
        return transaction.monoAs(actor, () -> createUser(request));
    }

    @Override
    public Mono<List<UserInfoDTO>> createBulk(List<CreateUserRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Mono.just(List.of());
        }
        return transaction.mono(() -> validateBulk(requests)
                .thenMany(Flux.fromIterable(requests).concatMap(this::createUser))
                .collectList());
    }

    @Override
    public Mono<UserInfoDTO> update(UUID id, UpdateUserRequest request) {
        return transaction.mono(() -> hashIfPresent(request.password())
                .flatMap(hash -> users.update(id, normalize(request.email()), request.name(), hash.orElse(null),
                        !Boolean.FALSE.equals(request.enabled()), Boolean.TRUE.equals(request.verified())))
                .flatMap(rows -> requireChanged(rows, id))
                .then(memberships.retainDepartments(id, request.departmentsOrEmpty()))
                .then(memberships.addDepartments(id, request.departmentsOrEmpty()))
                .then(memberships.retainRoles(id, request.rolesOrEmpty()))
                .then(memberships.addRoles(id, request.rolesOrEmpty()))
                .then(applyCustomFields(id, request.customFields(), WriteMode.REPLACE))
                .then(load(id)));
    }

    @Override
    public Mono<UserInfoDTO> patch(UUID id, PatchUserRequest request) {
        return transaction.mono(() -> hashIfPresent(request.password())
                .flatMap(hash -> users.patch(id, request.email() == null ? null : normalize(request.email()),
                        request.name(), hash.orElse(null), request.enabled(), request.verified()))
                .flatMap(rows -> requireChanged(rows, id))
                .then(addIfPresent(request.departments(), ids -> memberships.addDepartments(id, ids)))
                .then(addIfPresent(request.roles(), ids -> memberships.addRoles(id, ids)))
                .then(applyCustomFields(id, request.customFields(), WriteMode.MERGE))
                .then(load(id)));
    }

    @Override
    public Mono<Void> delete(Set<UUID> ids) {
        return transaction.mono(() -> users.deleteAll(ids)
                .flatMap(rows -> rows == 0 ? Mono.<Void>error(new NotFoundException("User not found")) : Mono.empty()));
    }

    private Mono<UserInfoDTO> createUser(CreateUserRequest request) {
        UUID id = UUID.randomUUID();
        String email = normalize(request.email());
        return hasher.hash(request.password())
                .flatMap(hash -> users.insert(id, email, request.name(), hash, request.enabled(), request.verified()))
                .then(memberships.addDepartments(id, request.departments()))
                .then(memberships.addRoles(id, request.roles()))
                .then(applyCustomFields(id, request.customFields(), WriteMode.CREATE))
                .then(users.findInfoById(id))
                .switchIfEmpty(Mono.fromSupplier(() -> new UserInfoDTO(id, email, request.name(), request.enabled(),
                        request.verified(), request.customFields(), OffsetDateTime.now(), null, List.of(), List.of())));
    }

    private Mono<Void> applyCustomFields(UUID id, Map<String, Object> values, WriteMode mode) {
        return contexts.forUser(id).flatMap(context ->
                customFields.apply(CustomFieldEntity.users, id, values, mode, context));
    }

    private Mono<Void> validateBulk(List<CreateUserRequest> requests) {
        List<String> emails = requests.stream().map(r -> normalize(r.email())).toList();
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = emails.stream().filter(e -> !seen.add(e)).collect(Collectors.toSet());
        if (!duplicates.isEmpty()) {
            return Mono.error(new BadRequestException("Duplicate emails in request: " + duplicates));
        }
        return users.findExistingEmails(emails).collectList().flatMap(existing -> existing.isEmpty()
                ? Mono.<Void>empty()
                : Mono.error(new ConflictException("Users already exist: " + existing)));
    }

    private Mono<java.util.Optional<String>> hashIfPresent(String password) {
        if (password == null) {
            return Mono.just(java.util.Optional.empty());
        }
        return hasher.hash(password).map(java.util.Optional::of);
    }

    private Mono<Void> addIfPresent(Set<UUID> ids, java.util.function.Function<Set<UUID>, Mono<Long>> action) {
        return ids == null || ids.isEmpty() ? Mono.empty() : action.apply(ids).then();
    }

    private Mono<UserInfoDTO> load(UUID id) {
        return users.findInfoById(id).switchIfEmpty(Mono.error(notFound(id)));
    }

    private Mono<Void> requireChanged(long rows, UUID id) {
        if (rows > 0) {
            return Mono.empty();
        }
        return users.findById(id)
                .flatMap(existing -> Mono.<Void>error(new ForbiddenException("You are not allowed to modify this user")))
                .switchIfEmpty(Mono.error(notFound(id)));
    }

    private NotFoundException notFound(UUID id) {
        return new NotFoundException("User " + id + " not found");
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
