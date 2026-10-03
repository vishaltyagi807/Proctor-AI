package dev.varshit.proctor.storageservice.service;

import dev.varshit.proctor.storageservice.dto.DownloadUrlResponse;
import dev.varshit.proctor.storageservice.dto.FileDTO;
import dev.varshit.proctor.storageservice.dto.UploadUrlRequest;
import dev.varshit.proctor.storageservice.dto.UploadUrlResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FileService {

    Flux<FileDTO> list(UUID complaintId);

    Mono<UploadUrlResponse> requestUpload(UUID complaintId, UploadUrlRequest request);

    Mono<FileDTO> confirmUpload(UUID complaintId, UUID fileId);

    Mono<DownloadUrlResponse> requestDownload(UUID complaintId, UUID fileId, boolean inline);

    Mono<Void> delete(UUID complaintId, UUID fileId);
}
