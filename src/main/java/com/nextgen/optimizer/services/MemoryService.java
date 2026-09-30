package com.nextgen.optimizer.services;

import com.nextgen.optimizer.nativeapi.WinNative;
import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Real system memory management through the Windows memory-list API
 * (the same mechanism used by RAMMap). Purging the standby list only discards
 * cached file pages, so it never closes applications or loses data.
 */
public class MemoryService {

    /** Snapshot of physical memory composition, in bytes. */
    public record MemoryState(long total, long inUse, long modified, long standby,
                              long standbyLowPriority, long free, boolean detailed) {
        public long available() { return standby + free; }
        public double inUsePercent() { return total > 0 ? inUse * 100.0 / total : 0; }
        public double freePercent() { return total > 0 ? free * 100.0 / total : 0; }
    }

    public enum CleanMode {
        /** Low-priority standby pages only: safe with a game running. */
        LIGHT,
        /** Modified list flush + full standby purge + page combining. */
        DEEP,
        /** Trims every process working set. Causes soft faults afterwards; not for use in-game. */
        WORKING_SETS
    }

    public record CleanResult(boolean success, long freedBytes, String message) {}

    private final GlobalMemory memory;
    private final long pageSize;
    private final Object cleanLock = new Object();

    private ScheduledExecutorService guardScheduler;
    private ScheduledFuture<?> guardTask;
    private volatile long lastGuardClean;
    private volatile long guardFreedTotal;
    private volatile int guardRuns;

    public MemoryService() {
        GlobalMemory mem = null;
        long page = 4096;
        try {
            mem = new SystemInfo().getHardware().getMemory();
            page = Math.max(4096, mem.getPageSize());
        } catch (Throwable ignored) {}
        this.memory = mem;
        this.pageSize = page;
    }

    public MemoryState readState() {
        long total = memory != null ? memory.getTotal() : 0;
        long[] lists = WinNative.queryMemoryLists();
        if (lists == null) {
            long available = memory != null ? memory.getAvailable() : 0;
            return new MemoryState(total, Math.max(0, total - available), 0, 0, 0, available, false);
        }
        long free = (lists[0] + lists[1]) * pageSize;
        long modified = (lists[2] + lists[3]) * pageSize;
        long standby = lists[5] * pageSize;
        long lowPriority = lists[6] * pageSize;
        long inUse = Math.max(0, total - free - modified - standby);
        return new MemoryState(total, inUse, modified, standby, lowPriority, free, true);
    }

    public boolean isNativeAvailable() {
        return WinNative.queryMemoryLists() != null;
    }

    /**
     * Runs a cleanup and reports how much memory moved to the free list.
     */
    public CleanResult clean(CleanMode mode) {
        synchronized (cleanLock) {
            MemoryState before = readState();
            boolean ok;
            switch (mode) {
                case LIGHT -> ok = WinNative.memoryListCommand(WinNative.MEMORY_PURGE_LOW_PRIORITY_STANDBY_LIST) == 0;
                case DEEP -> {
                    boolean flushed = WinNative.memoryListCommand(WinNative.MEMORY_FLUSH_MODIFIED_LIST) == 0;
                    boolean purged = WinNative.memoryListCommand(WinNative.MEMORY_PURGE_STANDBY_LIST) == 0;
                    WinNative.combineMemoryPages();
                    ok = flushed || purged;
                }
                case WORKING_SETS -> ok = WinNative.memoryListCommand(WinNative.MEMORY_EMPTY_WORKING_SETS) == 0;
                default -> ok = false;
            }
            MemoryState after = readState();
            long freed = Math.max(0, after.free() - before.free());
            if (!ok) {
                return new CleanResult(false, 0,
                        "O Windows negou a operação. Execute o NextGen X como administrador.");
            }
            return new CleanResult(true, freed, switch (mode) {
                case LIGHT -> "Cache de baixa prioridade liberado";
                case DEEP -> "Standby e lista modificada liberadas";
                case WORKING_SETS -> "Working sets reduzidos";
            });
        }
    }

    // ── RAM Guard ───────────────────────────────────────────────────────

    /**
     * Starts a background watchdog that purges cached memory when free memory
     * drops below {@code thresholdPercent}. {@code gameActive} decides whether
     * the guard should act when "only while gaming" is enabled.
     */
    public synchronized void startGuard(int thresholdPercent, boolean onlyWhileGaming,
                                        java.util.function.BooleanSupplier gameActive,
                                        Consumer<CleanResult> onClean) {
        stopGuard();
        int threshold = Math.max(5, Math.min(60, thresholdPercent));
        guardScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RamGuard");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        guardTask = guardScheduler.scheduleWithFixedDelay(() -> {
            try {
                if (onlyWhileGaming && !gameActive.getAsBoolean()) return;
                if (System.currentTimeMillis() - lastGuardClean < 90_000) return;
                MemoryState state = readState();
                if (!state.detailed() || state.freePercent() >= threshold) return;
                if (state.standby() < 256L * 1024 * 1024) return;
                CleanResult result = clean(CleanMode.LIGHT);
                if (result.success() && readState().freePercent() < threshold) {
                    result = clean(CleanMode.DEEP);
                }
                lastGuardClean = System.currentTimeMillis();
                if (result.success()) {
                    guardRuns++;
                    guardFreedTotal += result.freedBytes();
                    if (onClean != null) onClean.accept(result);
                }
            } catch (Throwable ignored) {}
        }, 5, 5, TimeUnit.SECONDS);
    }

    public synchronized void stopGuard() {
        if (guardTask != null) guardTask.cancel(false);
        if (guardScheduler != null) guardScheduler.shutdownNow();
        guardTask = null;
        guardScheduler = null;
    }

    public boolean isGuardRunning() {
        return guardTask != null && !guardTask.isCancelled();
    }

    public int getGuardRuns() { return guardRuns; }

    public long getGuardFreedTotal() { return guardFreedTotal; }

    public boolean isMemoryCompressionEnabled(PowerShellService ps) {
        String output = ps.executeSync("(Get-MMAgent).MemoryCompression");
        return "True".equalsIgnoreCase(output.trim());
    }
}
