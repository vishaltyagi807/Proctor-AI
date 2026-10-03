package dev.varshit.proctor.storageservice.service;

import dev.varshit.proctor.storageservice.config.StorageLimits;
import dev.varshit.proctor.storageservice.objectstore.ObjectStore;
import dev.varshit.proctor.storageservice.repository.FileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StaleUploadCleaner {

    private static final Logger log = LoggerFactory.getLogger(StaleUploadCleaner.class);

    private final FileRepository files;
    private final ObjectStore store;
    private final StorageLimits limits;

    public StaleUploadCleaner(FileRepository files, ObjectStore store, StorageLimits limits) {
        this.files = files;
        this.store = store;
        this.limits = limits;
    }

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT1M")
    public void purge() {
        files.purgeStale(limits.pendingTtl())
                .concatMap(key -> store.delete(key).onErrorResume(error -> {
                    log.warn("Unable to delete stale object {}", key, error);
                    return reactor.core.publisher.Mono.empty();
                }))
                .count()
                .subscribe(count -> {
                    if (count > 0) {
                        log.info("Removed {} stale pending uploads", count);
                    }
                }, error -> log.error("Stale upload cleanup failed", error));
    }
}
