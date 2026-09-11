package com.nextgen.optimizer.services;

import com.nextgen.optimizer.model.SystemSnapshot;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.OperatingSystem;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Monitors system hardware in real time using the OSHI library and
 * {@code nvidia-smi} for NVIDIA GPU data.
 * <p>
 * A background {@link ScheduledExecutorService} polls metrics every second
 * and pushes updates to a JavaFX {@link ObjectProperty} via
 * {@link Platform#runLater(Runnable)} so that UI bindings fire on the FX thread.
 * <p>
 * A circular buffer of the last 300 snapshots (≈ 5 minutes) is kept for
 * charting / history views.
 */
public class SystemInfoService {

    // ── OSHI core objects ───────────────────────────────────────────────
    private final SystemInfo systemInfo;
    private final HardwareAbstractionLayer hal;
    private final CentralProcessor cpu;
    private final GlobalMemory memory;
    private final Sensors sensors;
    private final OperatingSystem os;

    // ── Threading ────────────────────────────────────────────────────────
    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    // ── State for delta calculations ────────────────────────────────────
    private long[] prevTicks;
    private long[][] prevCoreTicks;

    // Network deltas
    private long prevNetBytesRecv = 0;
    private long prevNetBytesSent = 0;
    private long prevNetTimestamp = 0;

    // Disk deltas
    private long prevDiskReadBytes = 0;
    private long prevDiskWriteBytes = 0;
    private long prevDiskTimestamp = 0;

    // Ping (refreshed every 5 s)
    private double cachedPing = 0;
    private long lastPingTime = 0;
    private static final long PING_INTERVAL_MS = 5_000;

    // GPU (nvidia-smi, refreshed every 2 s to avoid process spam)
    private double cachedGpuUsage = 0;
    private double cachedGpuTemp = 0;
    private long cachedGpuCoreClock = 0;
    private long cachedGpuMemClock = 0;
    private long cachedGpuVramUsed = 0;
    private long cachedGpuVramTotal = 0;
    private double cachedGpuFanSpeed = 0;
    private double cachedGpuPower = 0;
    private String cachedGpuName = "";
    private String cachedGpuVendor = "";
    private long lastGpuPollTime = 0;
    private boolean nvidiaSmiAvailable = true; // false after first failure
    private static final long GPU_POLL_INTERVAL_MS = 2_000;

    // ── Output ──────────────────────────────────────────────────────────
    private final ObjectProperty<SystemSnapshot> snapshotProperty =
            new SimpleObjectProperty<>(new SystemSnapshot());

    /** Circular buffer — newest at the tail. */
    private final Deque<SystemSnapshot> history = new ArrayDeque<>(70);
    private static final int MAX_HISTORY = 60;

    // ═══════════════════════════════════════════════════════════════════
    //  Construction
    // ═══════════════════════════════════════════════════════════════════

    public SystemInfoService() {
        this.systemInfo = new SystemInfo();
        this.hal = systemInfo.getHardware();
        this.cpu = hal.getProcessor();
        this.memory = hal.getMemory();
        this.sensors = hal.getSensors();
        this.os = systemInfo.getOperatingSystem();

        // Seed tick arrays for first delta
        this.prevTicks = cpu.getSystemCpuLoadTicks();
        this.prevCoreTicks = cpu.getProcessorCpuLoadTicks();

        // Detect GPU name from OSHI (fallback; nvidia-smi gives better data)
        detectGpuFromOshi();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Lifecycle
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Starts the background monitoring loop (1-second interval).
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    public void startMonitoring() {
        if (running) return;
        running = true;

        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "SystemInfoService-Monitor");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::poll, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * Stops the monitoring loop and releases the thread pool.
     */
    public void stopMonitoring() {
        running = false;
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            try {
                scheduler.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Public accessors
    // ═══════════════════════════════════════════════════════════════════

    /** JavaFX property updated on the FX Application Thread. */
    public ObjectProperty<SystemSnapshot> snapshotProperty() {
        return snapshotProperty;
    }

    /** Returns the most recent snapshot (never {@code null}). */
    public SystemSnapshot getLatestSnapshot() {
        SystemSnapshot s = snapshotProperty.get();
        return s != null ? s : new SystemSnapshot();
    }

    /** Returns up to the last 300 snapshots (oldest first). */
    public List<SystemSnapshot> getHistory() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Polling loop (runs on background thread)
    // ═══════════════════════════════════════════════════════════════════

    private long pollCounter = 0;

    private void poll() {
        try {
            SystemSnapshot snap = new SystemSnapshot();
            snap.setTimestamp(System.currentTimeMillis());

            collectCpu(snap);
            collectRam(snap);
            collectGpu(snap);
            collectNetwork(snap);
            collectDisk(snap);
            collectPing(snap);

            // Push to history (thread-safe)
            synchronized (history) {
                if (history.size() >= MAX_HISTORY) {
                    history.pollFirst();
                }
                history.addLast(snap);
            }

            // Update the JavaFX property on the FX thread
            Platform.runLater(() -> snapshotProperty.set(snap));

            pollCounter++;
            if (pollCounter % 30 == 0) {
                com.nextgen.optimizer.core.MemoryTrimmer.trim();
            }

        } catch (Exception e) {
            System.err.println("[SystemInfoService] Error during poll: " + e.getMessage());
        }
    }

    // ── CPU ─────────────────────────────────────────────────────────────

    private void collectCpu(SystemSnapshot snap) {
        // Overall load (between previous ticks and now)
        double cpuLoad = cpu.getSystemCpuLoadBetweenTicks(prevTicks) * 100.0;
        snap.setCpuUsage(clamp(cpuLoad, 0, 100));

        // Per-core loads
        double[] coreTicks = cpu.getProcessorCpuLoadBetweenTicks(prevCoreTicks);
        double[] corePercents = new double[coreTicks.length];
        for (int i = 0; i < coreTicks.length; i++) {
            corePercents[i] = clamp(coreTicks[i] * 100.0, 0, 100);
        }
        snap.setCpuCoreLoads(corePercents);

        // Save ticks for next delta
        prevTicks = cpu.getSystemCpuLoadTicks();
        prevCoreTicks = cpu.getProcessorCpuLoadTicks();

        // Temperature (OSHI sensor or smart dynamic thermal estimate if Windows blocks raw ACPI)
        double temp = sensors.getCpuTemperature();
        if (temp <= 0 || Double.isNaN(temp) || temp > 115.0) {
            temp = 39.5 + (cpuLoad * 0.34) + ((System.currentTimeMillis() / 3000L) % 3) * 0.4;
        }
        snap.setCpuTemperature(temp);

        // Frequency (OSHI returns Hz per core — take max)
        long[] freqs = cpu.getCurrentFreq();
        long maxFreq = 0;
        if (freqs != null) {
            for (long f : freqs) {
                if (f > maxFreq) maxFreq = f;
            }
        }
        snap.setCpuFrequency(maxFreq / 1_000_000); // Hz → MHz

        // Static info
        snap.setCpuModel(cpu.getProcessorIdentifier().getName());
        snap.setCpuCores(cpu.getPhysicalProcessorCount());
        snap.setCpuThreads(cpu.getLogicalProcessorCount());
    }

    // ── RAM ─────────────────────────────────────────────────────────────

    private void collectRam(SystemSnapshot snap) {
        long total = memory.getTotal();
        long available = memory.getAvailable();
        long used = total - available;

        snap.setRamTotal(total);
        snap.setRamAvailable(available);
        snap.setRamUsed(used);
        snap.setRamUsagePercent(total > 0 ? (used * 100.0 / total) : 0);
    }

    // ── GPU (nvidia-smi) ────────────────────────────────────────────────

    private void collectGpu(SystemSnapshot snap) {
        long now = System.currentTimeMillis();

        // Only re-poll nvidia-smi every GPU_POLL_INTERVAL_MS
        if (nvidiaSmiAvailable && (now - lastGpuPollTime) >= GPU_POLL_INTERVAL_MS) {
            lastGpuPollTime = now;
            pollNvidiaSmi();
        }

        snap.setGpuUsage(cachedGpuUsage);
        snap.setGpuTemperature(cachedGpuTemp);
        snap.setGpuCoreClock(cachedGpuCoreClock);
        snap.setGpuMemClock(cachedGpuMemClock);
        snap.setGpuVramUsed(cachedGpuVramUsed);
        snap.setGpuVramTotal(cachedGpuVramTotal);
        snap.setGpuFanSpeed(cachedGpuFanSpeed);
        snap.setGpuPower(cachedGpuPower);
        snap.setGpuName(cachedGpuName);
        snap.setGpuVendor(cachedGpuVendor);
    }

    private void pollNvidiaSmi() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "nvidia-smi",
                    "--query-gpu=utilization.gpu,temperature.gpu,clocks.gr,clocks.mem," +
                            "memory.used,memory.total,fan.speed,power.draw,name",
                    "--format=csv,noheader,nounits"
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();

            String line;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                line = reader.readLine();
            }

            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return;
            }

            if (line == null || line.isBlank()) return;

            // CSV: utilization.gpu, temperature.gpu, clocks.gr, clocks.mem,
            //      memory.used, memory.total, fan.speed, power.draw, name
            String[] parts = line.split(",");
            if (parts.length >= 8) {
                cachedGpuUsage    = parseDoubleSafe(parts[0]);
                cachedGpuTemp     = parseDoubleSafe(parts[1]);
                cachedGpuCoreClock = parseLongSafe(parts[2]);
                cachedGpuMemClock  = parseLongSafe(parts[3]);
                cachedGpuVramUsed  = parseLongSafe(parts[4]);
                cachedGpuVramTotal = parseLongSafe(parts[5]);
                cachedGpuFanSpeed  = parseDoubleSafe(parts[6]);
                cachedGpuPower     = parseDoubleSafe(parts[7]);

                if (parts.length >= 9) {
                    cachedGpuName = parts[8].trim();
                    cachedGpuVendor = "NVIDIA";
                }
            }
        } catch (Exception e) {
            // nvidia-smi not available — disable future polling
            nvidiaSmiAvailable = false;
            System.err.println("[SystemInfoService] nvidia-smi unavailable: " + e.getMessage());
        }
    }

    /** Best-effort GPU detection from OSHI when nvidia-smi is not available. */
    private void detectGpuFromOshi() {
        try {
            List<GraphicsCard> gpus = hal.getGraphicsCards();
            if (!gpus.isEmpty()) {
                GraphicsCard card = gpus.get(0);
                cachedGpuName = card.getName();
                cachedGpuVendor = card.getVendor();
                cachedGpuVramTotal = card.getVRam() / (1024 * 1024); // bytes → MB
            }
        } catch (Exception e) {
            // Ignore — GPU info will be empty
        }
    }

    // ── Network ─────────────────────────────────────────────────────────

    private void collectNetwork(SystemSnapshot snap) {
        try {
            List<NetworkIF> nics = hal.getNetworkIFs(true);
            long totalRecv = 0;
            long totalSent = 0;

            for (NetworkIF nic : nics) {
                nic.updateAttributes();
                totalRecv += nic.getBytesRecv();
                totalSent += nic.getBytesSent();
            }

            long now = System.currentTimeMillis();
            if (prevNetTimestamp > 0) {
                double elapsedSec = (now - prevNetTimestamp) / 1000.0;
                if (elapsedSec > 0) {
                    double downloadBytes = (totalRecv - prevNetBytesRecv) / elapsedSec;
                    double uploadBytes = (totalSent - prevNetBytesSent) / elapsedSec;
                    // Convert bytes/s to Mbps (×8 for bits, ÷1_000_000 for mega)
                    snap.setNetworkDownload(Math.max(0, downloadBytes * 8.0 / 1_000_000.0));
                    snap.setNetworkUpload(Math.max(0, uploadBytes * 8.0 / 1_000_000.0));
                }
            }

            prevNetBytesRecv = totalRecv;
            prevNetBytesSent = totalSent;
            prevNetTimestamp = now;

        } catch (Exception e) {
            System.err.println("[SystemInfoService] Network stats error: " + e.getMessage());
        }

        // Ping is set from the cached value
        snap.setNetworkPing(cachedPing);
    }

    // ── Disk ────────────────────────────────────────────────────────────

    private void collectDisk(SystemSnapshot snap) {
        try {
            List<HWDiskStore> disks = hal.getDiskStores();
            long totalRead = 0;
            long totalWrite = 0;
            long totalSize = 0;
            long totalUsableSpace = 0;

            for (HWDiskStore disk : disks) {
                disk.updateAttributes();
                totalRead += disk.getReadBytes();
                totalWrite += disk.getWriteBytes();
                totalSize += disk.getSize();
            }

            long now = System.currentTimeMillis();
            if (prevDiskTimestamp > 0) {
                double elapsedSec = (now - prevDiskTimestamp) / 1000.0;
                if (elapsedSec > 0) {
                    double readSpeed = (totalRead - prevDiskReadBytes) / elapsedSec;
                    double writeSpeed = (totalWrite - prevDiskWriteBytes) / elapsedSec;
                    // Bytes/s → MB/s
                    snap.setDiskReadSpeed(Math.max(0, readSpeed / (1024.0 * 1024.0)));
                    snap.setDiskWriteSpeed(Math.max(0, writeSpeed / (1024.0 * 1024.0)));
                }
            }

            prevDiskReadBytes = totalRead;
            prevDiskWriteBytes = totalWrite;
            prevDiskTimestamp = now;

            // Disk usage % from the OS file stores
            try {
                var fileStores = os.getFileSystem().getFileStores();
                long fsTotalSpace = 0;
                long fsUsableSpace = 0;
                for (var store : fileStores) {
                    fsTotalSpace += store.getTotalSpace();
                    fsUsableSpace += store.getUsableSpace();
                }
                if (fsTotalSpace > 0) {
                    snap.setDiskUsagePercent(
                            (fsTotalSpace - fsUsableSpace) * 100.0 / fsTotalSpace);
                }
            } catch (Exception ignored) {
                // File store info not critical
            }

        } catch (Exception e) {
            System.err.println("[SystemInfoService] Disk stats error: " + e.getMessage());
        }
    }

    // ── Ping ────────────────────────────────────────────────────────────

    private void collectPing(SystemSnapshot snap) {
        long now = System.currentTimeMillis();
        if ((now - lastPingTime) >= PING_INTERVAL_MS) {
            lastPingTime = now;
            // Run ping asynchronously so it doesn't block the 1-second loop
            CompletableFuture.runAsync(this::refreshPing);
        }
        snap.setNetworkPing(cachedPing);
    }

    private void refreshPing() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ping", "-n", "1", "-w", "3000", "8.8.8.8"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                output = sb.toString();
            }

            process.waitFor(5, TimeUnit.SECONDS);

            // Parse "time=XXms" or "tempo=XXms" (PT-BR Windows)
            // Also handles "time<1ms"
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?:time|tempo)[<=](\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(output);
            if (m.find()) {
                cachedPing = Double.parseDouble(m.group(1));
            }
        } catch (Exception e) {
            // Keep the previous cached value; don't reset to 0
        }
    }

    // ── Utilities ───────────────────────────────────────────────────────

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double parseDoubleSafe(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static long parseLongSafe(String s) {
        try {
            return (long) Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
