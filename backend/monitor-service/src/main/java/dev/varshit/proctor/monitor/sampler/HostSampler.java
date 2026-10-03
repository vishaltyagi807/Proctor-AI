package dev.varshit.proctor.monitor.sampler;

import dev.varshit.proctor.monitor.dto.HostStats;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class HostSampler {

    private static final long REFRESH_DEVICES_MS = 60_000;
    private static final long REFRESH_FILE_STORES_MS = 10_000;
    private static final Set<String> VIRTUAL_FILE_SYSTEMS = Set.of("tmpfs", "devtmpfs", "squashfs", "overlay", "efivarfs",
            "ramfs", "autofs", "proc", "sysfs", "cgroup", "cgroup2", "nsfs", "fuse.snapfuse", "fuse.portal", "tracefs");
    private static final List<String> VIRTUAL_INTERFACES = List.of("lo", "veth", "docker", "br-", "virbr", "vmnet");

    private final HardwareAbstractionLayer hardware;
    private final OperatingSystem os;
    private final CentralProcessor processor;
    private final String osName;
    private final String cpuModel;

    private long[] previousTicks;
    private long[][] previousCoreTicks;
    private long previousAt;
    private long previousRead = -1;
    private long previousWrite = -1;
    private final Map<String, long[]> previousInterfaces = new HashMap<>();

    private List<NetworkIF> interfaces = List.of();
    private List<HWDiskStore> diskStores = List.of();
    private long devicesRefreshedAt;
    private List<HostStats.Disk> fileStores = List.of();
    private long fileStoresRefreshedAt;

    public HostSampler() {
        SystemInfo info = new SystemInfo();
        this.hardware = info.getHardware();
        this.os = info.getOperatingSystem();
        this.processor = hardware.getProcessor();
        this.osName = (os.getFamily() + " " + os.getVersionInfo().getVersion()).trim();
        this.cpuModel = processor.getProcessorIdentifier().getName().trim();
    }

    public synchronized HostStats sample() {
        long now = System.currentTimeMillis();
        double seconds = previousAt == 0 ? 0 : (now - previousAt) / 1000.0;
        refreshDevices(now);

        long[] ticks = processor.getSystemCpuLoadTicks();
        long[][] coreTicks = processor.getProcessorCpuLoadTicks();
        double cpu = previousTicks == null ? 0 : finite(processor.getSystemCpuLoadBetweenTicks(previousTicks, ticks));
        double[] cores = previousCoreTicks == null
                ? new double[coreTicks.length]
                : processor.getProcessorCpuLoadBetweenTicks(previousCoreTicks, coreTicks);
        previousTicks = ticks;
        previousCoreTicks = coreTicks;

        GlobalMemory memory = hardware.getMemory();
        long memoryTotal = memory.getTotal();
        long memoryUsed = Math.max(0, memoryTotal - memory.getAvailable());

        long read = 0;
        long write = 0;
        for (HWDiskStore disk : diskStores) {
            disk.updateAttributes();
            read += disk.getReadBytes();
            write += disk.getWriteBytes();
        }
        long readRate = rate(previousRead, read, seconds);
        long writeRate = rate(previousWrite, write, seconds);
        previousRead = read;
        previousWrite = write;

        List<HostStats.Network> networks = new ArrayList<>();
        long rxTotal = 0;
        long txTotal = 0;
        Set<String> seen = new HashSet<>();
        for (NetworkIF network : interfaces) {
            network.updateAttributes();
            String name = network.getName();
            seen.add(name);
            long[] previous = previousInterfaces.get(name);
            long rx = previous == null ? 0 : rate(previous[0], network.getBytesRecv(), seconds);
            long tx = previous == null ? 0 : rate(previous[1], network.getBytesSent(), seconds);
            previousInterfaces.put(name, new long[]{network.getBytesRecv(), network.getBytesSent()});
            rxTotal += rx;
            txTotal += tx;
            String[] addresses = network.getIPv4addr();
            networks.add(new HostStats.Network(name, addresses.length == 0 ? null : addresses[0], rx, tx, network.getSpeed()));
        }
        previousInterfaces.keySet().retainAll(seen);
        previousAt = now;

        return new HostStats(
                os.getNetworkParams().getHostName(),
                osName,
                cpuModel,
                processor.getPhysicalProcessorCount(),
                processor.getLogicalProcessorCount(),
                os.getSystemUptime(),
                os.getProcessCount(),
                os.getThreadCount(),
                cpu,
                Arrays.stream(cores).map(HostSampler::finite).boxed().toList(),
                Arrays.stream(processor.getSystemLoadAverage(3)).map(value -> value < 0 ? 0 : value).boxed().toList(),
                memoryTotal,
                memoryUsed,
                memory.getVirtualMemory().getSwapTotal(),
                memory.getVirtualMemory().getSwapUsed(),
                fileStores(now),
                readRate,
                writeRate,
                networks,
                rxTotal,
                txTotal);
    }

    private void refreshDevices(long now) {
        if (now - devicesRefreshedAt < REFRESH_DEVICES_MS) {
            return;
        }
        interfaces = hardware.getNetworkIFs(false).stream()
                .filter(network -> VIRTUAL_INTERFACES.stream().noneMatch(prefix -> network.getName().startsWith(prefix)))
                .toList();
        diskStores = hardware.getDiskStores();
        previousRead = -1;
        previousWrite = -1;
        devicesRefreshedAt = now;
    }

    private List<HostStats.Disk> fileStores(long now) {
        if (now - fileStoresRefreshedAt < REFRESH_FILE_STORES_MS) {
            return fileStores;
        }
        Set<String> volumes = new HashSet<>();
        List<HostStats.Disk> disks = new ArrayList<>();
        for (OSFileStore store : os.getFileSystem().getFileStores(true)) {
            if (store.getTotalSpace() <= 0 || VIRTUAL_FILE_SYSTEMS.contains(store.getType())
                    || store.getMount().startsWith("/snap/") || store.getMount().startsWith("/etc/")
                    || !volumes.add(store.getVolume())) {
                continue;
            }
            disks.add(new HostStats.Disk(store.getMount(), store.getType(), store.getTotalSpace(),
                    Math.max(0, store.getTotalSpace() - store.getUsableSpace())));
        }
        fileStores = disks;
        fileStoresRefreshedAt = now;
        return disks;
    }

    private static long rate(long previous, long current, double seconds) {
        if (previous < 0 || seconds <= 0 || current < previous) {
            return 0;
        }
        return Math.round((current - previous) / seconds);
    }

    static double finite(double value) {
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }
}
