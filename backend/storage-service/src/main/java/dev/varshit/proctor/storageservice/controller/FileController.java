package dev.varshit.proctor.storageservice.controller;

import dev.varshit.proctor.storageservice.dto.DownloadUrlResponse;
import dev.varshit.proctor.storageservice.dto.FileDTO;
import dev.varshit.proctor.storageservice.dto.UploadUrlRequest;
import dev.varshit.proctor.storageservice.dto.UploadUrlResponse;
import dev.varshit.proctor.storageservice.service.FileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/complaints/{complaintId}/files")
public class FileController {

    private final FileService service;

    public FileController(FileService service) {
        this.service = service;
    }

    @GetMapping
    public Flux<FileDTO> list(@PathVariable UUID complaintId) {
        return service.list(complaintId);
    }

    @PostMapping("/upload-url")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<UploadUrlResponse> requestUpload(@PathVariable UUID complaintId,
                                                 @Valid @RequestBody UploadUrlRequest request) {
        return service.requestUpload(complaintId, request);
    }

    @PostMapping("/{fileId}/confirm")
    public Mono<FileDTO> confirm(@PathVariable UUID complaintId, @PathVariable UUID fileId) {
        return service.confirmUpload(complaintId, fileId);
    }

    @GetMapping("/{fileId}/download-url")
    public Mono<DownloadUrlResponse> download(@PathVariable UUID complaintId, @PathVariable UUID fileId,
                                              @RequestParam(defaultValue = "false") boolean inline) {
        return service.requestDownload(complaintId, fileId, inline);
    }

    @DeleteMapping("/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable UUID complaintId, @PathVariable UUID fileId) {
        return service.delete(complaintId, fileId);
    }
}
