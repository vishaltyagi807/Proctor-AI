package dev.varshit.proctor.storageservice.service;

import dev.varshit.proctor.common.exception.BadRequestException;
import dev.varshit.proctor.common.exception.ConflictException;
import dev.varshit.proctor.common.exception.ForbiddenException;
import dev.varshit.proctor.common.exception.NotFoundException;
import dev.varshit.proctor.persistence.rls.SecureTransaction;
import dev.varshit.proctor.storageservice.config.StorageLimits;
import dev.varshit.proctor.storageservice.dto.DownloadUrlResponse;
import dev.varshit.proctor.storageservice.dto.FileDTO;
import dev.varshit.proctor.storageservice.dto.FileRef;
import dev.varshit.proctor.storageservice.dto.FileUsage;
import dev.varshit.proctor.storageservice.dto.UploadUrlRequest;
import dev.varshit.proctor.storageservice.dto.UploadUrlResponse;
import dev.varshit.proctor.storageservice.objectstore.ObjectStore;
import dev.varshit.proctor.storageservice.policy.UploadPolicy;
import dev.varshit.proctor.storageservice.repository.FileRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class FileServiceImpl implements FileService {

    private static final String UPLOADED = "uploaded";

    private final FileRepository files;
    private final ObjectStore store;
    private final UploadPolicy policy;
    private final StorageLimits limits;
    private final SecureTransaction transaction;

    public FileServiceImpl(FileRepository files, ObjectStore store, UploadPolicy policy, StorageLimits limits,
                           SecureTransaction transaction) {
        this.files = files;
        this.store = store;
        this.policy = policy;
        this.limits = limits;
        this.transaction = transaction;
    }

    @Override
    public Flux<FileDTO> list(UUID complaintId) {
        return transaction.flux(() -> requireComplaint(complaintId).thenMany(files.findByComplaint(complaintId)));
    }

    @Override
    public Mono<UploadUrlResponse> requestUpload(UUID complaintId, UploadUrlRequest request) {
        var approved = policy.approve(request.fileName(), request.contentType(), request.sizeBytes());
        UUID fileId = UUID.randomUUID();
        String key = "complaints/" + complaintId + "/" + fileId + "." + approved.extension();
        return transaction.mono(() -> requireComplaint(complaintId)
                .then(files.usage(complaintId))
                .flatMap(usage -> enforceQuota(usage, request.sizeBytes()))
                .then(files.insertPending(fileId, complaintId, approved.displayName(), approved.contentType(),
                        request.sizeBytes(), key))
                .then(Mono.fromSupplier(() -> {
                    var presigned = store.presignUpload(key, approved.contentType(), request.sizeBytes());
                    return new UploadUrlResponse(fileId, presigned.url(), presigned.method(), presigned.headers(),
                            presigned.expiresAt());
                })));
    }

    @Override
    public Mono<FileDTO> confirmUpload(UUID complaintId, UUID fileId) {
        return transaction.mono(() -> loadRef(complaintId, fileId).flatMap(ref -> UPLOADED.equals(ref.status())
                ? files.findById(fileId)
                : verifyAndPublish(ref)));
    }

    @Override
    public Mono<DownloadUrlResponse> requestDownload(UUID complaintId, UUID fileId, boolean inline) {
        return transaction.mono(() -> loadRef(complaintId, fileId).flatMap(ref -> {
            if (!UPLOADED.equals(ref.status())) {
                return Mono.error(new ConflictException("File upload has not been completed"));
            }
            boolean viewInline = inline && isInlineViewable(ref.contentType());
            var presigned = store.presignDownload(ref.fileUrl(), ref.fileName(), ref.contentType(), viewInline);
            return Mono.just(new DownloadUrlResponse(presigned.url(), ref.fileName(), ref.contentType(),
                    presigned.expiresAt()));
        }));
    }

    @Override
    public Mono<Void> delete(UUID complaintId, UUID fileId) {
        return transaction.mono(() -> loadRef(complaintId, fileId)
                .flatMap(ref -> files.delete(fileId)
                        .flatMap(rows -> rows == 0
                                ? Mono.<Void>error(new ForbiddenException("You are not allowed to delete this file"))
                                : store.delete(ref.fileUrl()))));
    }

    private Mono<FileDTO> verifyAndPublish(FileRef ref) {
        return store.stat(ref.fileUrl())
                .switchIfEmpty(Mono.error(new BadRequestException("Upload has not been completed")))
                .flatMap(meta -> {
                    boolean valid = meta.sizeBytes() == ref.sizeBytes()
                            && ref.contentType().equalsIgnoreCase(meta.contentType());
                    if (!valid) {
                        return store.delete(ref.fileUrl())
                                .then(files.delete(ref.id()))
                                .then(Mono.<FileDTO>error(new BadRequestException(
                                        "Uploaded object does not match the declared size or content type")));
                    }
                    return files.markUploaded(ref.id())
                            .flatMap(rows -> rows == 0
                                    ? Mono.<FileDTO>error(new ForbiddenException("You are not allowed to confirm this upload"))
                                    : files.findById(ref.id()));
                });
    }

    private Mono<Void> enforceQuota(FileUsage usage, long incomingBytes) {
        if (usage.fileCount() >= limits.maxFilesPerComplaint()) {
            return Mono.error(new ConflictException("This complaint already has the maximum number of files"));
        }
        if (usage.totalBytes() + incomingBytes > limits.maxBytesPerComplaint()) {
            return Mono.error(new ConflictException("This complaint has reached its storage quota"));
        }
        return Mono.empty();
    }

    private Mono<FileRef> loadRef(UUID complaintId, UUID fileId) {
        return files.findRef(complaintId, fileId).switchIfEmpty(Mono.error(new NotFoundException("File not found")));
    }

    private Mono<Void> requireComplaint(UUID complaintId) {
        return files.complaintVisible(complaintId)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(new NotFoundException("Complaint " + complaintId + " not found")))
                .then();
    }

    private boolean isInlineViewable(String contentType) {
        return contentType.startsWith("image/") || contentType.startsWith("video/") || contentType.startsWith("audio/");
    }
}
