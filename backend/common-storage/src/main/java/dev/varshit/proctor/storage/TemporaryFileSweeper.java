package dev.varshit.proctor.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

public class TemporaryFileSweeper implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TemporaryFileSweeper.class);

    private final FileStorage storage;
    private final StorageProperties properties;
    private volatile Disposable task;

    public TemporaryFileSweeper(FileStorage storage, StorageProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    @Override
    public void start() {
        task = Flux.interval(Duration.ZERO, properties.sweepInterval())
                .onBackpressureDrop()
                .concatMap(tick -> storage.purgeOlderThan(properties.tempTtl())
                        .onErrorResume(error -> {
                            log.warn("Temporary file sweep failed", error);
                            return Mono.empty();
                        }))
                .subscribe(count -> {
                    if (count > 0) {
                        log.info("Removed {} leftover temporary files", count);
                    }
                });
    }

    @Override
    public void stop() {
        Disposable current = task;
        if (current != null) {
            current.dispose();
        }
    }

    @Override
    public boolean isRunning() {
        Disposable current = task;
        return current != null && !current.isDisposed();
    }
}
