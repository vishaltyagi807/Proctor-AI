package dev.varshit.proctor.monitor.sampler;

import dev.varshit.proctor.monitor.dto.DatabaseStats;
import dev.varshit.proctor.monitor.dto.DatabaseView;
import dev.varshit.proctor.monitor.dto.HistoryPoint;
import dev.varshit.proctor.monitor.dto.HostStats;
import dev.varshit.proctor.monitor.dto.RedisStats;
import dev.varshit.proctor.monitor.dto.ServiceStats;
import dev.varshit.proctor.monitor.dto.StorageStats;
import dev.varshit.proctor.monitor.dto.SystemOverview;
import dev.varshit.proctor.monitor.dto.SystemSnapshot;
import dev.varshit.proctor.monitor.repository.SystemStatsRepository;
import dev.varshit.proctor.monitoring.InstanceSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class SystemSampler implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {

    public static final Duration INTERVAL = Duration.ofSeconds(2);

    private static final Logger log = LoggerFactory.getLogger(SystemSampler.class);
    private static final Duration STORAGE_INTERVAL = Duration.ofSeconds(30);
    private static final Duration PART_TIMEOUT = Duration.ofSeconds(3);
    private static final long STALE_AFTER_MS = 6_000;
    private static final int HISTORY_SIZE = 150;
    private static final double[] NO_RATES = {Double.NaN, Double.NaN, Double.NaN, Double.NaN};

    private final HostSampler host;
    private final InstanceCollector instances;
    private final RedisInfoSampler redis;
    private final SystemStatsRepository stats;

    private final Sinks.Many<SystemSnapshot> updates = Sinks.many().multicast().directBestEffort();
    private final Deque<HistoryPoint> history = new ArrayDeque<>();
    private final Map<String, InstanceSnapshot> previousInstances = new HashMap<>();
    private final Map<String, double[]> lastRates = new HashMap<>();

    private volatile SystemSnapshot latest;
    private volatile Disposable task;
    private DatabaseStats previousDatabase;
    private long previousDatabaseAt;
    private DatabaseView lastDatabase;
    private volatile StorageStats storage;
    private volatile long storageAt;

    public SystemSampler(HostSampler host, InstanceCollector instances, RedisInfoSampler redis, SystemStatsRepository stats) {
        this.host = host;
        this.instances = instances;
        this.redis = redis;
        this.stats = stats;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (task != null) {
            return;
        }
        task = Flux.interval(Duration.ZERO, INTERVAL)
                .onBackpressureDrop()
                .concatMap(tick -> collect().onErrorResume(error -> {
                    log.warn("System sample failed: {}", error.getMessage());
                    return Mono.empty();
                }))
                .subscribe(this::publish);
    }

    @Override
    public void destroy() {
        Disposable running = task;
        if (running != null) {
            running.dispose();
        }
        updates.tryEmitComplete();
    }

    public Flux<SystemSnapshot> updates() {
        return updates.asFlux();
    }

    public SystemOverview overview() {
        synchronized (history) {
            return new SystemOverview((int) INTERVAL.toSeconds(), latest, List.copyOf(history));
        }
    }

    private Mono<SystemSnapshot> collect() {
        long started = System.currentTimeMillis();
        List<String> issues = Collections.synchronizedList(new ArrayList<>());
        Mono<Optional<StorageStats>> storagePart = started - storageAt < STORAGE_INTERVAL.toMillis()
                ? Mono.just(Optional.ofNullable(storage))
                : part("Storage", stats.storage().doOnNext(fresh -> {
            storage = fresh;
            storageAt = started;
        }), issues).map(fresh -> fresh.or(() -> Optional.ofNullable(storage)));
        return Mono.zip(
                part("Host", Mono.fromCallable(host::sample).subscribeOn(Schedulers.boundedElastic()), issues),
                part("Service reports", instances.snapshots(), issues),
                part("Service registry", instances.registrations(), issues),
                part("Database", stats.database(), issues),
                part("Redis", redis.sample(), issues),
                storagePart
        ).map(parts -> build(System.currentTimeMillis(),
                parts.getT1().orElse(null),
                parts.getT2().orElse(List.of()),
                parts.getT3().orElse(List.of()),
                parts.getT4().orElse(null),
                parts.getT5().orElse(null),
                parts.getT6().orElse(null),
                List.copyOf(issues)));
    }

    private <T> Mono<Optional<T>> part(String name, Mono<T> source, List<String> issues) {
        return source.timeout(PART_TIMEOUT)
                .map(Optional::of)
                .onErrorResume(error -> {
                    issues.add(name + " unavailable: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
                    return Mono.just(Optional.empty());
                })
                .defaultIfEmpty(Optional.empty());
    }

    private SystemSnapshot build(long now, HostStats hostStats, List<InstanceSnapshot> snapshots,
                                 List<InstanceCollector.Registration> registrations, DatabaseStats databaseStats,
                                 RedisStats redisStats, StorageStats storageStats, List<String> issues) {
        List<ServiceStats> services = services(now, snapshots, registrations);
        DatabaseView database = database(now, databaseStats);
        return new SystemSnapshot(now, hostStats, services, database, redisStats, storageStats,
                point(now, hostStats, services, database, redisStats), issues);
    }

    private List<ServiceStats> services(long now, List<InstanceSnapshot> snapshots,
                                        List<InstanceCollector.Registration> registrations) {
        List<ServiceStats> result = new ArrayList<>();
        Set<String> current = new HashSet<>();
        Set<String> reporting = new HashSet<>();
        for (InstanceSnapshot snapshot : snapshots) {
            current.add(snapshot.instanceId());
            reporting.add(snapshot.service() + ":" + snapshot.port());
            double[] rates = rates(snapshot);
            long age = Math.max(0, now - snapshot.timestamp());
            InstanceSnapshot.Memory memory = snapshot.memory();
            InstanceSnapshot.DbPool pool = snapshot.dbPool();
            result.add(new ServiceStats(
                    snapshot.service(),
                    snapshot.instanceId(),
                    snapshot.host(),
                    snapshot.port(),
                    age > STALE_AFTER_MS ? "stale" : "up",
                    registrations.stream().anyMatch(registration -> registration.service().equals(snapshot.service())
                            && registration.port() == snapshot.port()),
                    age,
                    Math.max(0, now - snapshot.startedAt()),
                    round(HostSampler.finite(snapshot.cpu().process()), 4),
                    memory.heapUsed(),
                    memory.heapMax() < 0 ? null : memory.heapMax(),
                    memory.nonHeapUsed(),
                    memory.rss(),
                    snapshot.threads().live(),
                    nullable(rates[0]),
                    nullable(rates[1]),
                    nullable(rates[2]),
                    nullable(rates[3]),
                    pool == null ? null : pool.acquired(),
                    pool == null ? null : pool.pending(),
                    pool == null ? null : pool.max()));
        }
        for (InstanceCollector.Registration registration : registrations) {
            if (reporting.add(registration.service() + ":" + registration.port())) {
                result.add(new ServiceStats(registration.service(),
                        registration.service() + "@" + registration.host() + ":" + registration.port(),
                        registration.host(), registration.port(), "silent", true,
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));
            }
        }
        previousInstances.keySet().retainAll(current);
        lastRates.keySet().retainAll(current);
        result.sort(Comparator.comparing(ServiceStats::service).thenComparingInt(ServiceStats::port));
        return result;
    }

    private double[] rates(InstanceSnapshot snapshot) {
        InstanceSnapshot previous = previousInstances.get(snapshot.instanceId());
        if (previous != null && previous.timestamp() == snapshot.timestamp()) {
            return lastRates.getOrDefault(snapshot.instanceId(), NO_RATES);
        }
        double[] rates = NO_RATES.clone();
        if (previous != null && snapshot.timestamp() > previous.timestamp()) {
            double seconds = (snapshot.timestamp() - previous.timestamp()) / 1000.0;
            if (snapshot.http() != null && previous.http() != null) {
                long requests = snapshot.http().requests() - previous.http().requests();
                if (requests >= 0) {
                    rates[0] = requests / seconds;
                    rates[1] = Math.max(0, snapshot.http().errors() - previous.http().errors()) / seconds;
                    if (requests > 0) {
                        rates[2] = Math.max(0, snapshot.http().totalTimeMs() - previous.http().totalTimeMs()) / requests;
                    }
                }
            }
            long gc = snapshot.gc().timeMs() - previous.gc().timeMs();
            if (gc >= 0) {
                rates[3] = gc / seconds;
            }
        }
        previousInstances.put(snapshot.instanceId(), snapshot);
        lastRates.put(snapshot.instanceId(), rates);
        return rates;
    }

    private DatabaseView database(long now, DatabaseStats current) {
        if (current == null) {
            return null;
        }
        DatabaseStats previous = previousDatabase;
        double seconds = previous == null ? 0 : (now - previousDatabaseAt) / 1000.0;
        long blocks = current.blocksHit() + current.blocksRead();
        double hitRatio = blocks == 0 ? 1 : (double) current.blocksHit() / blocks;
        double transactions = 0;
        double rollbacks = 0;
        double reads = 0;
        double writes = 0;
        if (previous != null && seconds > 0 && current.commits() >= previous.commits()) {
            long committed = current.commits() - previous.commits();
            long rolledBack = Math.max(0, current.rollbacks() - previous.rollbacks());
            transactions = (committed + rolledBack) / seconds;
            rollbacks = rolledBack / seconds;
            reads = Math.max(0, current.rowsReturned() - previous.rowsReturned()) / seconds;
            writes = Math.max(0, (current.rowsInserted() + current.rowsUpdated() + current.rowsDeleted())
                    - (previous.rowsInserted() + previous.rowsUpdated() + previous.rowsDeleted())) / seconds;
            long hit = current.blocksHit() - previous.blocksHit();
            long read = current.blocksRead() - previous.blocksRead();
            if (hit >= 0 && read >= 0 && hit + read > 0) {
                hitRatio = (double) hit / (hit + read);
            }
        } else if (previous != null && lastDatabase != null) {
            transactions = lastDatabase.transactionRate();
            rollbacks = lastDatabase.rollbackRate();
            reads = lastDatabase.rowsReadRate();
            writes = lastDatabase.rowsWrittenRate();
        }
        previousDatabase = current;
        previousDatabaseAt = now;
        lastDatabase = new DatabaseView(current.version(), current.startedAt(), current.sizeBytes(), current.maxConnections(),
                current.connections(), current.active(), current.idle(), current.idleInTransaction(), current.waiting(),
                round(current.longestQuerySeconds(), 2), round(transactions, 2), round(rollbacks, 2), round(hitRatio, 4),
                round(reads, 1), round(writes, 1), current.deadlocks(), current.tempBytes(),
                current.tables() == null ? List.of() : current.tables());
        return lastDatabase;
    }

    private HistoryPoint point(long now, HostStats hostStats, List<ServiceStats> services, DatabaseView database,
                               RedisStats redisStats) {
        Map<String, Double> serviceCpu = new LinkedHashMap<>();
        Map<String, Long> serviceHeap = new LinkedHashMap<>();
        double requests = 0;
        double errors = 0;
        for (ServiceStats service : services) {
            if (service.cpu() != null) {
                serviceCpu.put(service.instanceId(), service.cpu());
            }
            if (service.heapUsed() != null) {
                serviceHeap.put(service.instanceId(), service.heapUsed());
            }
            requests += service.requestRate() == null ? 0 : service.requestRate();
            errors += service.errorRate() == null ? 0 : service.errorRate();
        }
        return new HistoryPoint(
                now,
                hostStats == null ? 0 : round(hostStats.cpu(), 4),
                hostStats == null || hostStats.memoryTotal() == 0 ? 0 : round((double) hostStats.memoryUsed() / hostStats.memoryTotal(), 4),
                hostStats == null ? 0 : hostStats.networkRxRate(),
                hostStats == null ? 0 : hostStats.networkTxRate(),
                hostStats == null ? 0 : hostStats.diskReadRate(),
                hostStats == null ? 0 : hostStats.diskWriteRate(),
                round(requests, 2),
                round(errors, 2),
                database == null ? 0 : database.connections(),
                database == null ? 0 : database.transactionRate(),
                redisStats == null ? 0 : redisStats.opsPerSecond(),
                serviceCpu,
                serviceHeap);
    }

    private void publish(SystemSnapshot snapshot) {
        latest = snapshot;
        synchronized (history) {
            history.addLast(snapshot.point());
            while (history.size() > HISTORY_SIZE) {
                history.removeFirst();
            }
        }
        updates.tryEmitNext(snapshot);
    }

    private static Double nullable(double value) {
        return Double.isFinite(value) ? round(value, 2) : null;
    }

    private static double round(double value, int places) {
        if (!Double.isFinite(value)) {
            return 0;
        }
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}
