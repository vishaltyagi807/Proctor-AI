package dev.varshit.proctor.notification.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.varshit.proctor.notification.crypto.SecretCipher;
import dev.varshit.proctor.notification.repository.SystemNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

@Component
public class FcmCredentialsProvider {

    private static final Logger log = LoggerFactory.getLogger(FcmCredentialsProvider.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final SystemNotificationRepository repository;
    private final SecretCipher cipher;
    private final ObjectMapper mapper;
    private volatile Mono<Optional<FcmCredentials>> cached;

    public FcmCredentialsProvider(SystemNotificationRepository repository, SecretCipher cipher, ObjectMapper mapper) {
        this.repository = repository;
        this.cipher = cipher;
        this.mapper = mapper;
        this.cached = load().cache(CACHE_TTL);
    }

    public Mono<Optional<FcmCredentials>> current() {
        return cached;
    }

    public void invalidate() {
        this.cached = load().cache(CACHE_TTL);
    }

    private Mono<Optional<FcmCredentials>> load() {
        return Mono.defer(() -> repository.integration("fcm")
                .filter(row -> row.enabled() && row.secretCiphertext() != null)
                .map(row -> Optional.of(FcmCredentials.parse(mapper, cipher.decrypt(row.secretCiphertext()))))
                .defaultIfEmpty(Optional.empty())
                .onErrorResume(error -> {
                    log.error("Unable to load FCM configuration: {}", error.getMessage());
                    return Mono.just(Optional.empty());
                }));
    }
}
