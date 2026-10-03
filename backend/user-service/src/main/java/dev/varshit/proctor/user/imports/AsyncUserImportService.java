package dev.varshit.proctor.user.imports;

import dev.varshit.proctor.notifications.progress.ProgressBroker;
import dev.varshit.proctor.security.UserPrincipal;
import dev.varshit.proctor.storage.FileStorage;
import dev.varshit.proctor.storage.StoredFile;
import dev.varshit.proctor.user.dto.CreateUserRequest;
import dev.varshit.proctor.user.service.UserService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class AsyncUserImportService implements UserImportService {

    private static final Logger log = LoggerFactory.getLogger(AsyncUserImportService.class);
    private static final Set<String> EXTENSIONS = Set.of("csv", "json", "xlsx");
    private static final String KIND = "user-import";

    private final FileStorage storage;
    private final UserImportParser parser;
    private final ImportLookupRepository lookup;
    private final UserService userService;
    private final ProgressBroker progress;
    private final Validator validator;

    public AsyncUserImportService(FileStorage storage, UserImportParser parser, ImportLookupRepository lookup,
                                  UserService userService, ProgressBroker progress, Validator validator) {
        this.storage = storage;
        this.parser = parser;
        this.lookup = lookup;
        this.userService = userService;
        this.progress = progress;
        this.validator = validator;
    }

    @Override
    public Mono<Started> start(FilePart file, UserPrincipal actor) {
        return lookup.load()
                .flatMap(reference -> storage.store(file, "imports", EXTENSIONS)
                        .flatMap(stored -> storage.resolve(stored.key())
                                .flatMap(path -> parser.parse(path, reference))
                                .flatMap(rows -> {
                                    String processId = UUID.randomUUID().toString();
                                    run(processId, stored, rows, reference, actor);
                                    return Mono.just(new Started(processId, rows.size()));
                                })
                                .onErrorResume(error -> storage.delete(stored.key()).then(Mono.error(error)))));
    }

    private void run(String processId, StoredFile stored, List<ImportRow> rows, ImportReferenceData reference,
                     UserPrincipal actor) {
        int total = rows.size();
        AtomicInteger processed = new AtomicInteger();
        publish(actor.id(), "process-progress", processId, "running", processed.get(), total,
                "Importing " + total + " users...");
        Flux.fromIterable(rows)
                .concatMap(row -> importOne(processId, row, reference, actor)
                        .doOnSuccess(unused -> publish(actor.id(), "process-progress", processId, "running",
                                processed.incrementAndGet(), total, null)))
                .then(Mono.defer(() -> storage.delete(stored.key())))
                .doOnSuccess(unused -> publish(actor.id(), "process-done", processId, "done", processed.get(), total,
                        "Imported " + processed.get() + " of " + total + " users."))
                .doOnError(error -> publish(actor.id(), "process-error", processId, "error", processed.get(), total,
                        messageOf(error)))
                .onErrorResume(error -> Mono.empty())
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    private Mono<Void> importOne(String processId, ImportRow row, ImportReferenceData reference, UserPrincipal actor) {
        List<String> unresolved = new ArrayList<>();
        Set<UUID> roleIds = resolve(row.roleNames(), reference.roleIdsByName(), unresolved, "role");
        Set<UUID> departmentIds = resolve(row.departmentCodes(), reference.departmentIdsByCode(), unresolved, "department");
        if (!unresolved.isEmpty()) {
            publish(actor.id(), "process-error", processId, "running", null, null,
                    (row.email() == null ? "row" : row.email()) + ": unknown " + String.join(", ", unresolved));
            return Mono.empty();
        }

        CreateUserRequest request = new CreateUserRequest(row.name(), row.email(), row.password(), roleIds,
                departmentIds, row.customFields(), row.enabled(), row.verified());
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining(", "));
            publish(actor.id(), "process-error", processId, "running", null, null, request.email() + ": " + message);
            return Mono.empty();
        }
        return userService.createAs(actor, request)
                .then()
                .onErrorResume(error -> {
                    publish(actor.id(), "process-error", processId, "running", null, null,
                            request.email() + ": " + messageOf(error));
                    return Mono.empty();
                });
    }

    private Set<UUID> resolve(Set<String> keys, Map<String, UUID> byKey, List<String> unresolved, String kind) {
        Set<UUID> ids = new HashSet<>();
        for (String key : keys) {
            UUID id = byKey.get(key.toLowerCase(java.util.Locale.ROOT));
            if (id == null) {
                unresolved.add(kind + " '" + key + "'");
            } else {
                ids.add(id);
            }
        }
        return ids;
    }

    private void publish(UUID userId, String event, String processId, String status, Integer processed, Integer total,
                         String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("processId", processId);
        data.put("kind", KIND);
        data.put("label", "Importing users");
        data.put("status", status);
        if (processed != null) {
            data.put("processed", processed);
        }
        if (total != null) {
            data.put("total", total);
        }
        if (message != null) {
            data.put("message", message);
        }
        progress.publish(userId, event, data).subscribe(unused -> {
        }, error -> log.warn("Could not publish progress event", error));
    }

    private String messageOf(Throwable error) {
        return error.getMessage() == null ? "Import failed" : error.getMessage();
    }
}
