package dev.varshit.proctor.auth.service;

import dev.varshit.proctor.auth.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCleaner {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleaner.class);
    private static final int RETENTION_DAYS = 7;

    private final RefreshTokenRepository repository;

    public RefreshTokenCleaner(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT2M")
    public void purge() {
        repository.purgeExpired(RETENTION_DAYS).subscribe(
                count -> {
                    if (count > 0) {
                        log.info("Removed {} expired refresh tokens", count);
                    }
                },
                error -> log.error("Refresh token cleanup failed", error));
    }
}
