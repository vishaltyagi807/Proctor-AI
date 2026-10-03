package dev.varshit.proctor.faceservice.service;

import dev.varshit.proctor.common.exception.BadRequestException;
import dev.varshit.proctor.common.exception.ForbiddenException;
import dev.varshit.proctor.common.exception.NotFoundException;
import dev.varshit.proctor.faceservice.config.FaceModelProperties;
import dev.varshit.proctor.faceservice.dto.EnrollmentDTO;
import dev.varshit.proctor.faceservice.dto.MatchedUserDTO;
import dev.varshit.proctor.faceservice.embedding.EmbeddingCodec;
import dev.varshit.proctor.faceservice.repository.FaceAccessGuard;
import dev.varshit.proctor.faceservice.repository.FaceAuditRepository;
import dev.varshit.proctor.faceservice.repository.FaceEmbeddingJdbcRepository;
import dev.varshit.proctor.faceservice.repository.FaceSearchRepository;
import dev.varshit.proctor.faceservice.repository.UserLookupRepository;
import dev.varshit.proctor.faceservice.vision.FaceAligner;
import dev.varshit.proctor.faceservice.vision.FaceBox;
import dev.varshit.proctor.faceservice.vision.FaceDetector;
import dev.varshit.proctor.faceservice.vision.FaceEmbedder;
import dev.varshit.proctor.faceservice.vision.ImageCodec;
import dev.varshit.proctor.persistence.rls.SecureTransaction;
import dev.varshit.proctor.security.CurrentUser;
import dev.varshit.proctor.security.UserPrincipal;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.awt.image.BufferedImage;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class FaceEnrollmentService {

    private final FaceDetector detector;
    private final FaceEmbedder embedder;
    private final VectorStore vectorStore;
    private final FaceAccessGuard accessGuard;
    private final UserLookupRepository userLookup;
    private final FaceEmbeddingJdbcRepository jdbcRepository;
    private final FaceSearchRepository searchRepository;
    private final FaceAuditRepository auditRepository;
    private final SecureTransaction transaction;
    private final FaceModelProperties properties;

    public FaceEnrollmentService(
            @Lazy FaceDetector detector,
            @Lazy FaceEmbedder embedder,
            VectorStore vectorStore,
            FaceAccessGuard accessGuard,
            UserLookupRepository userLookup,
            FaceEmbeddingJdbcRepository jdbcRepository,
            FaceSearchRepository searchRepository,
            FaceAuditRepository auditRepository,
            SecureTransaction transaction,
            FaceModelProperties properties) {
        this.detector = detector;
        this.embedder = embedder;
        this.vectorStore = vectorStore;
        this.accessGuard = accessGuard;
        this.userLookup = userLookup;
        this.jdbcRepository = jdbcRepository;
        this.searchRepository = searchRepository;
        this.auditRepository = auditRepository;
        this.transaction = transaction;
        this.properties = properties;
    }

    public Mono<EnrollmentDTO> enroll(UUID userId, String base64Image) {
        return CurrentUser.require().flatMap(actor -> enrollAs(actor, userId, base64Image));
    }

    public Mono<EnrollmentDTO> enrollAs(UserPrincipal actor, UUID userId, String base64Image) {
        return Mono.fromCallable(() -> jdbcRepository.findSelfEnrollCount(userId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(existing -> {
                    String action = existing.isPresent() ? FaceAccessGuard.UPDATE : FaceAccessGuard.WRITE;
                    return accessGuard.requireManage(actor, action, userId)
                            .then(accessGuard.isPrivileged(actor, action, userId))
                            .flatMap(privileged -> transaction.monoAs(actor, () -> userLookup.findEnabledById(userId))
                                    .switchIfEmpty(Mono.error(new NotFoundException("User " + userId + " not found")))
                                    .flatMap(user -> enrollCommon(user, existing, base64Image, privileged))
                                    .flatMap(enrollment -> transaction.monoAs(actor, () -> auditRepository.log(
                                                    existing.isPresent() ? "replace" : "enroll", userId, null, null, null,
                                                    privileged ? null : "self-service"))
                                            .thenReturn(enrollment)));
                });
    }

    public Mono<EnrollmentDTO> enrollByEmailAs(UserPrincipal actor, String email, String base64Image) {
        return transaction.monoAs(actor, () -> userLookup.findEnabledByEmail(email))
                .switchIfEmpty(Mono.error(new NotFoundException("No enabled user found with email " + email)))
                .flatMap(user -> enrollAs(actor, user.id(), base64Image));
    }

    public Mono<Void> removeEnrollment(UUID userId) {
        return CurrentUser.require()
                .flatMap(actor -> accessGuard.requireManage(actor, FaceAccessGuard.DELETE, userId)
                        .then(Mono.fromRunnable(() -> vectorStore.delete(List.of(userId.toString())))
                                .subscribeOn(Schedulers.boundedElastic()))
                        .then(transaction.monoAs(actor, () -> auditRepository.log("remove", userId, null, null, null, null))));
    }

    public Mono<Void> unlock(UUID userId) {
        return CurrentUser.require()
                .flatMap(actor -> accessGuard.requireUnlock(actor, userId)
                        .then(Mono.fromCallable(() -> jdbcRepository.resetSelfEnrollCount(userId))
                                .subscribeOn(Schedulers.boundedElastic()))
                        .flatMap(rows -> rows == 0
                                ? Mono.<Void>error(new NotFoundException("No face enrollment found for user " + userId))
                                : transaction.monoAs(actor, () -> auditRepository.log("unlock", userId, null, null, null, null))));
    }

    public Mono<List<EnrollmentDTO>> listEnrollments() {
        return accessGuard.requireListEnrollments()
                .then(transaction.mono(() -> searchRepository.listVisible(properties.selfEnrollLimit()).collectList()));
    }

    private Mono<EnrollmentDTO> enrollCommon(MatchedUserDTO user, Optional<Integer> existing, String base64Image,
                                             boolean privileged) {
        int currentCount = existing.orElse(0);
        if (!privileged && currentCount >= properties.selfEnrollLimit()) {
            return Mono.error(new ForbiddenException(
                    "Self-enrollment limit reached (" + properties.selfEnrollLimit()
                            + "). Ask someone with department or organization-wide"
                            + " face enrollment access to update your face."));
        }
        int newCount = privileged ? 0 : currentCount + 1;
        return Mono.fromCallable(() -> extractLargestFaceEmbedding(base64Image))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(embedding -> storeEmbedding(user, embedding, newCount, privileged));
    }

    private float[] extractLargestFaceEmbedding(String base64Image) {
        byte[] bytes = ImageCodec.decodeBase64(base64Image);
        if (bytes.length > properties.maxImageBytes()) {
            throw new BadRequestException("Image exceeds the maximum allowed size");
        }
        BufferedImage image = ImageCodec.decode(bytes);
        List<FaceBox> faces = detector.detect(image);
        if (faces.isEmpty()) {
            throw new BadRequestException("No face detected in the image");
        }
        FaceBox largest = faces.stream().max(Comparator.comparingDouble(FaceBox::area)).orElseThrow();
        BufferedImage aligned = FaceAligner.align(image, largest.landmarks(), 112);
        if (aligned == null) {
            throw new BadRequestException("Could not align the detected face");
        }
        return embedder.embed(aligned);
    }

    private Mono<EnrollmentDTO> storeEmbedding(MatchedUserDTO user, float[] embedding, int selfEnrollCount, boolean privileged) {
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> metadata = Map.of(
                "name", user.name(),
                "email", user.email(),
                "enrolledAt", now.toString(),
                "selfEnrollCount", selfEnrollCount);
        Document document = new Document(user.id().toString(), EmbeddingCodec.encode(embedding), metadata);
        boolean locked = !privileged && selfEnrollCount >= properties.selfEnrollLimit();
        return Mono.fromRunnable(() -> vectorStore.add(List.of(document)))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(new EnrollmentDTO(user.id(), user.name(), user.email(), now, selfEnrollCount, locked, null, null, null));
    }
}
