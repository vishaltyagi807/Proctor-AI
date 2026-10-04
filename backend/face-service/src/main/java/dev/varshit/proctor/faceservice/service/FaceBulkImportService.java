package dev.varshit.proctor.faceservice.service;

import dev.varshit.proctor.common.exception.BadRequestException;
import dev.varshit.proctor.faceservice.repository.FaceAccessGuard;
import dev.varshit.proctor.faceservice.repository.FaceAuditRepository;
import dev.varshit.proctor.persistence.rls.SecureTransaction;
import dev.varshit.proctor.notifications.progress.ProgressBroker;
import dev.varshit.proctor.security.UserPrincipal;
import dev.varshit.proctor.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class FaceBulkImportService {

    private static final Logger log = LoggerFactory.getLogger(FaceBulkImportService.class);
    private static final Set<String> ZIP_EXTENSION = Set.of("zip");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "bmp");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final String KIND = "face-import";

    public record Started(String processId, int total) {
    }

    private record Entry(String fileName, String email, byte[] bytes) {
    }

    private final FileStorage storage;
    private final FaceEnrollmentService enrollmentService;
    private final ProgressBroker progress;
    private final FaceAccessGuard accessGuard;
    private final FaceAuditRepository auditRepository;
    private final SecureTransaction transaction;

    public FaceBulkImportService(FileStorage storage, FaceEnrollmentService enrollmentService, ProgressBroker progress,
                                 FaceAccessGuard accessGuard, FaceAuditRepository auditRepository,
                                 SecureTransaction transaction) {
        this.storage = storage;
        this.enrollmentService = enrollmentService;
        this.progress = progress;
        this.accessGuard = accessGuard;
        this.auditRepository = auditRepository;
        this.transaction = transaction;
    }

    public Mono<Started> start(FilePart file, UserPrincipal actor) {
        return accessGuard.requireImport(actor, FaceAccessGuard.WRITE)
                .then(Mono.usingWhen(
                        storage.store(file, "face-imports", ZIP_EXTENSION),
                        stored -> storage.resolve(stored.key()).flatMap(this::readEntries),
                        stored -> storage.delete(stored.key())))
                .flatMap(entries -> {
                    String processId = UUID.randomUUID().toString();
                    return transaction.monoAs(actor, () -> auditRepository.log("bulk_import", null, null, null,
                                    entries.size(), file.filename()))
                            .then(Mono.fromRunnable(() -> run(processId, entries, actor)))
                            .thenReturn(new Started(processId, entries.size()));
                })
                .doFinally(signal -> storage.release(file).subscribe());
    }

    private Mono<List<Entry>> readEntries(Path zipPath) {
        return Mono.fromCallable(() -> parseZip(zipPath)).subscribeOn(Schedulers.boundedElastic());
    }

    private List<Entry> parseZip(Path zipPath) throws IOException {
        List<Entry> entries = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry zipEntry;
            while ((zipEntry = zip.getNextEntry()) != null) {
                if (zipEntry.isDirectory()) {
                    continue;
                }
                String name = zipEntry.getName();
                int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
                String fileName = slash < 0 ? name : name.substring(slash + 1);
                if (fileName.startsWith(".") || fileName.startsWith("__MACOSX")) {
                    continue;
                }
                int dot = fileName.lastIndexOf('.');
                if (dot < 0 || !IMAGE_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT))) {
                    continue;
                }
                String stem = fileName.substring(0, dot);
                entries.add(new Entry(fileName, stem, zip.readAllBytes()));
            }
        }
        if (entries.isEmpty()) {
            throw new BadRequestException("The zip has no image files (expected filenames like "
                    + "student0001@college.com.jpg, one per enrolled user)");
        }
        return entries;
    }

    private void run(String processId, List<Entry> entries, UserPrincipal actor) {
        int total = entries.size();
        AtomicInteger processed = new AtomicInteger();
        publish(actor.id(), "process-progress", processId, "running", processed.get(), total,
                "Importing " + total + " face photos...");
        Flux.fromIterable(entries)
                .concatMap(entry -> importOne(processId, entry, actor)
                        .doOnSuccess(unused -> publish(actor.id(), "process-progress", processId, "running",
                                processed.incrementAndGet(), total, null)))
                .then()
                .doOnSuccess(unused -> publish(actor.id(), "process-done", processId, "done", processed.get(), total,
                        "Processed " + processed.get() + " of " + total + " photos."))
                .doOnError(error -> publish(actor.id(), "process-error", processId, "error", processed.get(), total,
                        messageOf(error)))
                .onErrorResume(error -> Mono.empty())
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    private Mono<Void> importOne(String processId, Entry entry, UserPrincipal actor) {
        if (!EMAIL.matcher(entry.email()).matches()) {
            publish(actor.id(), "process-error", processId, "running", null, null,
                    entry.fileName() + ": filename is not a valid email");
            return Mono.empty();
        }
        return enrollmentService.enrollByEmailAs(actor, entry.email(), Base64.getEncoder().encodeToString(entry.bytes()))
                .then()
                .onErrorResume(error -> {
                    publish(actor.id(), "process-error", processId, "running", null, null,
                            entry.email() + ": " + messageOf(error));
                    return Mono.empty();
                });
    }

    private void publish(UUID userId, String event, String processId, String status, Integer processedCount,
                         Integer total, String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("processId", processId);
        data.put("kind", KIND);
        data.put("label", "Importing faces from ZIP");
        data.put("status", status);
        if (processedCount != null) {
            data.put("processed", processedCount);
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
        return error.getMessage() == null ? "Enroll failed" : error.getMessage();
    }
}
