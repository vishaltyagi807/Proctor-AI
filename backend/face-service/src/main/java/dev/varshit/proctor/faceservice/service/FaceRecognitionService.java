package dev.varshit.proctor.faceservice.service;

import dev.varshit.proctor.common.exception.BadRequestException;
import dev.varshit.proctor.faceservice.config.FaceModelProperties;
import dev.varshit.proctor.faceservice.dto.BoundingBoxDTO;
import dev.varshit.proctor.faceservice.dto.FaceMatchDTO;
import dev.varshit.proctor.faceservice.dto.MatchedUserDTO;
import dev.varshit.proctor.faceservice.dto.RecognizeResponse;
import dev.varshit.proctor.faceservice.repository.FaceAccessGuard;
import dev.varshit.proctor.faceservice.repository.FaceAuditRepository;
import dev.varshit.proctor.faceservice.repository.FaceSearchRepository;
import dev.varshit.proctor.faceservice.vision.FaceAligner;
import dev.varshit.proctor.faceservice.vision.FaceBox;
import dev.varshit.proctor.faceservice.vision.FaceDetector;
import dev.varshit.proctor.faceservice.vision.FaceEmbedder;
import dev.varshit.proctor.faceservice.vision.ImageCodec;
import dev.varshit.proctor.persistence.rls.SecureTransaction;
import dev.varshit.proctor.security.CurrentUser;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FaceRecognitionService {

    private record Detected(BufferedImage image, List<FaceBox> faces) {
    }

    private record Matched(FaceBox box, boolean matched, Float confidence, MatchedUserDTO user) {
    }

    private static final long LIVE_AUDIT_WINDOW_MS = 60_000;
    private static final int LIVE_SIGHTINGS_LIMIT = 10_000;

    private final Map<String, Long> liveSightings = new ConcurrentHashMap<>();

    private final FaceDetector detector;
    private final FaceEmbedder embedder;
    private final FaceAccessGuard accessGuard;
    private final FaceSearchRepository searchRepository;
    private final FaceAuditRepository auditRepository;
    private final SecureTransaction transaction;
    private final FaceModelProperties properties;

    public FaceRecognitionService(
            @Lazy FaceDetector detector,
            @Lazy FaceEmbedder embedder,
            FaceAccessGuard accessGuard,
            FaceSearchRepository searchRepository,
            FaceAuditRepository auditRepository,
            SecureTransaction transaction,
            FaceModelProperties properties) {
        this.detector = detector;
        this.embedder = embedder;
        this.accessGuard = accessGuard;
        this.searchRepository = searchRepository;
        this.auditRepository = auditRepository;
        this.transaction = transaction;
        this.properties = properties;
    }

    public Mono<RecognizeResponse> recognize(String base64Image, boolean live) {
        return accessGuard.requireRecognize(live)
                .then(Mono.fromCallable(() -> processImage(base64Image)).subscribeOn(Schedulers.boundedElastic()))
                .flatMap(detected -> resolveMatches(detected, live));
    }

    private Detected processImage(String base64Image) {
        byte[] bytes = ImageCodec.decodeBase64(base64Image);
        if (bytes.length > properties.maxImageBytes()) {
            throw new BadRequestException("Image exceeds the maximum allowed size");
        }
        BufferedImage image = ImageCodec.decode(bytes);
        return new Detected(image, detector.detect(image));
    }

    private Mono<RecognizeResponse> resolveMatches(Detected detected, boolean live) {
        return Flux.fromIterable(detected.faces())
                .concatMap(box -> matchFace(detected.image(), box, live))
                .collectList()
                .flatMap(matches -> audit(matches, live).thenReturn(matches))
                .map(matches -> respond(detected.image(), matches, live));
    }

    private Mono<Void> audit(List<Matched> matches, boolean live) {
        if (!live) {
            return write("recognize", matches, matches.size());
        }
        return CurrentUser.require().flatMap(actor -> {
            List<Matched> fresh = matches.stream().filter(match -> firstLiveSighting(actor.id(), match)).toList();
            return fresh.isEmpty() ? Mono.<Void>empty() : write("live_recognize", fresh, matches.size());
        });
    }

    private Mono<Void> write(String event, List<Matched> matches, int faceCount) {
        if (matches.isEmpty()) {
            return transaction.mono(() -> auditRepository.log(event, null, false, null, 0, null));
        }
        return transaction.mono(() -> Flux.fromIterable(matches)
                .concatMap(match -> auditRepository.log(event,
                        match.matched() ? match.user().id() : null,
                        match.matched(),
                        match.confidence(),
                        faceCount,
                        null))
                .then());
    }

    private boolean firstLiveSighting(UUID actorId, Matched match) {
        long now = System.currentTimeMillis();
        if (liveSightings.size() > LIVE_SIGHTINGS_LIMIT) {
            liveSightings.values().removeIf(seenAt -> now - seenAt > LIVE_AUDIT_WINDOW_MS);
        }
        String key = actorId + ":" + (match.matched() ? match.user().id() : "unknown");
        Long previous = liveSightings.get(key);
        if (previous != null && now - previous < LIVE_AUDIT_WINDOW_MS) {
            return false;
        }
        liveSightings.put(key, now);
        return true;
    }

    private Mono<Matched> matchFace(BufferedImage image, FaceBox box, boolean live) {
        return Mono.fromCallable(() -> {
                    BufferedImage aligned = FaceAligner.align(image, box.landmarks(), 112);
                    return aligned == null ? new float[0] : embedder.embed(aligned);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(embedding -> embedding.length == 0
                        ? Mono.just(new Matched(box, false, null, null))
                        : searchBestMatch(box, embedding, live));
    }

    private Mono<Matched> searchBestMatch(FaceBox box, float[] embedding, boolean live) {
        return transaction.mono(() -> searchRepository.findBestMatch(embedding, live))
                .map(candidate -> candidate.visible() && candidate.score() >= properties.matchThreshold()
                        ? new Matched(box, true,
                        candidate.detailed() ? (float) candidate.score() : null,
                        new MatchedUserDTO(candidate.userId(), candidate.name(), candidate.detailed() ? candidate.email() : null))
                        : new Matched(box, false, null, null))
                .defaultIfEmpty(new Matched(box, false, null, null));
    }

    private RecognizeResponse respond(BufferedImage image, List<Matched> matches, boolean live) {
        List<FaceMatchDTO> faceDtos = new ArrayList<>();
        for (Matched match : matches) {
            faceDtos.add(new FaceMatchDTO(
                    new BoundingBoxDTO(
                            Math.round(match.box().x()),
                            Math.round(match.box().y()),
                            Math.round(match.box().width()),
                            Math.round(match.box().height())),
                    match.confidence(),
                    match.matched(),
                    match.matched() ? match.user() : null));
        }
        String encoded = live ? "" : ImageCodec.encodeBase64(ImageCodec.encodePng(image));
        return new RecognizeResponse(encoded, image.getWidth(), image.getHeight(), faceDtos);
    }
}
