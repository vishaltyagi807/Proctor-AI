package dev.varshit.proctor.monitoring;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

public class InstanceSampler {

    private static final Path PROC_STATUS = Path.of("/proc/self/status");

    private final String service;
    private final String instanceId;
    private final String host;
    private final String address;
    private final int port;
    private final ObjectProvider<MeterRegistry> registry;

    private final RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
    private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    private final OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();

    public InstanceSampler(String service, String host, String address, int port, ObjectProvider<MeterRegistry> registry) {
        this.service = service;
        this.host = host;
        this.address = address;
        this.port = port;
        this.instanceId = service + "@" + host + ":" + port;
        this.registry = registry;
    }

    public String instanceId() {
        return instanceId;
    }

    public InstanceSnapshot sample() {
        MeterRegistry meters = registry.getIfAvailable();
        return new InstanceSnapshot(
                service,
                instanceId,
                host,
                address,
                port,
                runtime.getPid(),
                runtime.getStartTime(),
                System.currentTimeMillis(),
                cpu(),
                memory(),
                new InstanceSnapshot.Threads(threads.getThreadCount(), threads.getDaemonThreadCount(), threads.getPeakThreadCount()),
                gc(),
                ManagementFactory.getClassLoadingMXBean().getLoadedClassCount(),
                meters == null ? null : http(meters),
                meters == null ? null : dbPool(meters));
    }

    private InstanceSnapshot.Cpu cpu() {
        double process = 0;
        double system = 0;
        if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
            process = Math.max(0, sun.getProcessCpuLoad());
            system = Math.max(0, sun.getCpuLoad());
        }
        return new InstanceSnapshot.Cpu(process, system, os.getAvailableProcessors(), Math.max(0, os.getSystemLoadAverage()));
    }

    private InstanceSnapshot.Memory memory() {
        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
        long direct = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class).stream()
                .filter(pool -> "direct".equals(pool.getName()))
                .mapToLong(BufferPoolMXBean::getMemoryUsed)
                .sum();
        return new InstanceSnapshot.Memory(heap.getUsed(), heap.getCommitted(), heap.getMax(), nonHeap.getUsed(),
                nonHeap.getCommitted(), direct, residentSetSize());
    }

    private InstanceSnapshot.Gc gc() {
        long count = 0;
        long time = 0;
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            count += Math.max(0, collector.getCollectionCount());
            time += Math.max(0, collector.getCollectionTime());
        }
        return new InstanceSnapshot.Gc(count, time);
    }

    private InstanceSnapshot.Http http(MeterRegistry meters) {
        Collection<Timer> timers = meters.find("http.server.requests").timers();
        long requests = 0;
        long errors = 0;
        double total = 0;
        for (Timer timer : timers) {
            requests += timer.count();
            total += timer.totalTime(TimeUnit.MILLISECONDS);
            if ("SERVER_ERROR".equals(timer.getId().getTag("outcome"))) {
                errors += timer.count();
            }
        }
        return new InstanceSnapshot.Http(requests, errors, total);
    }

    private InstanceSnapshot.DbPool dbPool(MeterRegistry meters) {
        Collection<Gauge> max = meters.find("r2dbc.pool.max.allocated").gauges();
        if (max.isEmpty()) {
            return null;
        }
        return new InstanceSnapshot.DbPool(
                sum(meters, "r2dbc.pool.acquired"),
                sum(meters, "r2dbc.pool.idle"),
                sum(meters, "r2dbc.pool.pending"),
                (int) max.stream().mapToDouble(Gauge::value).sum());
    }

    private int sum(MeterRegistry meters, String name) {
        return (int) meters.find(name).gauges().stream().mapToDouble(Gauge::value).filter(Double::isFinite).sum();
    }

    private Long residentSetSize() {
        if (!Files.isReadable(PROC_STATUS)) {
            return null;
        }
        try {
            for (String line : Files.readAllLines(PROC_STATUS)) {
                if (line.startsWith("VmRSS:")) {
                    String[] parts = line.substring(6).trim().split("\\s+");
                    return Long.parseLong(parts[0]) * 1024L;
                }
            }
        } catch (IOException | NumberFormatException ignored) {
            return null;
        }
        return null;
    }
}
