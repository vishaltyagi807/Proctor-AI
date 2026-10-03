package dev.varshit.proctor.faceservice.controller;

import dev.varshit.proctor.faceservice.dto.EnrollRequest;
import dev.varshit.proctor.faceservice.dto.EnrollmentDTO;
import dev.varshit.proctor.faceservice.dto.RecognizeRequest;
import dev.varshit.proctor.faceservice.dto.RecognizeResponse;
import dev.varshit.proctor.faceservice.imports.FaceZipTemplateGenerator;
import dev.varshit.proctor.faceservice.repository.FaceAccessGuard;
import dev.varshit.proctor.faceservice.repository.FaceAuditRepository;
import dev.varshit.proctor.faceservice.dto.FaceAuditDTO;
import dev.varshit.proctor.common.dto.PageResponse;
import dev.varshit.proctor.common.exception.BadRequestException;
import dev.varshit.proctor.persistence.rls.SecureTransaction;
import dev.varshit.proctor.faceservice.service.FaceBulkImportService;
import dev.varshit.proctor.faceservice.service.FaceEnrollmentService;
import dev.varshit.proctor.faceservice.service.FaceRecognitionService;
import dev.varshit.proctor.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/faces")
public class FaceController {

    private final FaceEnrollmentService enrollmentService;
    private final FaceRecognitionService recognitionService;
    private final FaceBulkImportService bulkImportService;
    private final FaceZipTemplateGenerator templateGenerator;
    private final FaceAccessGuard accessGuard;
    private final FaceAuditRepository auditRepository;
    private final SecureTransaction transaction;

    public FaceController(FaceEnrollmentService enrollmentService, FaceRecognitionService recognitionService,
                          FaceBulkImportService bulkImportService, FaceZipTemplateGenerator templateGenerator,
                          FaceAccessGuard accessGuard, FaceAuditRepository auditRepository, SecureTransaction transaction) {
        this.enrollmentService = enrollmentService;
        this.recognitionService = recognitionService;
        this.bulkImportService = bulkImportService;
        this.templateGenerator = templateGenerator;
        this.accessGuard = accessGuard;
        this.auditRepository = auditRepository;
        this.transaction = transaction;
    }

    @PostMapping("/enroll/{userId}")
    public Mono<EnrollmentDTO> enroll(@PathVariable UUID userId, @Valid @RequestBody EnrollRequest request) {
        return enrollmentService.enroll(userId, request.image());
    }

    @PostMapping("/recognize")
    public Mono<RecognizeResponse> recognize(@Valid @RequestBody RecognizeRequest request) {
        return recognitionService.recognize(request.image(), request.live());
    }

    @GetMapping("/enrollments")
    public Mono<List<EnrollmentDTO>> enrollments() {
        return enrollmentService.listEnrollments();
    }

    @DeleteMapping("/enrollments/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> removeEnrollment(@PathVariable UUID userId) {
        return enrollmentService.removeEnrollment(userId);
    }

    @PostMapping("/enrollments/{userId}/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> unlock(@PathVariable UUID userId) {
        return enrollmentService.unlock(userId);
    }

    @GetMapping("/audit")
    public Mono<PageResponse<FaceAuditDTO>> audit(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String event,
            @RequestParam(required = false) UUID subjectId) {
        if (event != null && !FaceAuditRepository.EVENTS.contains(event)) {
            return Mono.error(new BadRequestException("Unknown event " + event));
        }
        int safeSize = Math.max(1, Math.min(size, 100));
        return accessGuard.requireAudit()
                .then(transaction.mono(() -> auditRepository.search(event, subjectId, Math.max(0, page), safeSize)));
    }

    @GetMapping("/enroll/bulk/template")
    public Mono<ResponseEntity<byte[]>> bulkTemplate(@RequestParam(defaultValue = "csv") String format) {
        return CurrentUser.require()
                .flatMap(principal -> accessGuard.requireImport(principal, FaceAccessGuard.READ))
                .then(Mono.fromCallable(() -> templateGenerator.generate(format))
                        .subscribeOn(Schedulers.boundedElastic()))
                .map(template -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(template.contentType()))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment().filename(template.fileName()).build().toString())
                        .body(template.bytes()));
    }

    @PostMapping(value = "/enroll/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Map<String, Object>> enrollBulk(@RequestPart("file") FilePart file) {
        return CurrentUser.require()
                .flatMap(principal -> bulkImportService.start(file, principal))
                .map(started -> Map.<String, Object>of(
                        "message", "Import started",
                        "processId", started.processId(),
                        "total", started.total()));
    }
}
