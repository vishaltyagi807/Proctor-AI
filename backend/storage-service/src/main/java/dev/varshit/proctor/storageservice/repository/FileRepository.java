package dev.varshit.proctor.storageservice.repository;

import dev.varshit.proctor.storageservice.dto.FileDTO;
import dev.varshit.proctor.storageservice.dto.FileRef;
import dev.varshit.proctor.storageservice.dto.FileUsage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

public interface FileRepository {

    Mono<Boolean> complaintVisible(UUID complaintId);

    Mono<FileUsage> usage(UUID complaintId);

    Flux<FileDTO> findByComplaint(UUID complaintId);

    Mono<FileDTO> findById(UUID id);

    Mono<FileRef> findRef(UUID complaintId, UUID id);

    Mono<Long> insertPending(UUID id, UUID complaintId, String fileName, String contentType, long sizeBytes,
                             String key);

    Mono<Long> markUploaded(UUID id);

    Mono<Long> delete(UUID id);

    Flux<String> purgeStale(Duration olderThan);
}
