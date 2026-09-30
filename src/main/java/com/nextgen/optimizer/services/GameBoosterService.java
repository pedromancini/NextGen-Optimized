package com.nextgen.optimizer.services;

import com.google.gson.Gson;
import com.nextgen.optimizer.nativeapi.WinNative;
import com.nextgen.optimizer.tweaks.PowerPlanAction;
import com.nextgen.optimizer.tweaks.TweakContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Watches for games starting and applies session-only boosts: high CPU
 * priority for the game, a performance power plan and a light RAM cleanup.
 * Everything temporary is undone when the last game closes. The original power
 * plan is also written to disk, so it is restored even after a crash.
 */
public class GameBoosterService {

    public static final Set<String> DEFAULT_GAMES = Set.of("cs2.exe");

    public record Options(boolean highPriority, boolean powerPlan, boolean cleanRam) {}

    public interface Listener {
        void onGameStarted(String exe, String summary);
        void onGameStopped(String exe);
    }

    private final TweakContext ctx;
    private final MemoryService memory;
    private final Path sessionFile = Path.of(System.getProperty("user.home"), ".nextgen", "booster-session.json");
    private final PowerPlanAction planAction = new PowerPlanAction();
    private final Gson gson = new Gson();

    private ScheduledExecutorService scheduler;
    private volatile Set<String> games = new LinkedHashSet<>(DEFAULT_GAMES);
    private volatile Options options = new Options(true, true, true);
    private volatile Listener listener;
    private volatile String activeGame;
    private volatile long activePid = -1;
    private Map<String, String> savedPlan;

    public GameBoosterService(TweakContext ctx, MemoryService memory) {
        this.ctx = ctx;
        this.memory = memory;
    }

    public void setListener(Listener listener) { this.listener = listener; }

    public void configure(Set<String> gameExecutables, Options opts) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String g : gameExecutables) {
            String n = g.trim().toLowerCase(Locale.ROOT);
            if (!n.isEmpty()) normalized.add(n.endsWith(".exe") ? n : n + ".exe");
        }
        if (normalized.isEmpty()) normalized.addAll(DEFAULT_GAMES);
        this.games = normalized;
        this.options = opts;
    }

    public Set<String> games() { return games; }

    public synchronized void start() {
        if (scheduler != null) return;
        recoverStaleSession();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "GameBooster");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::tick, 1, 3, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (activeGame != null) endSession();
    }

    public boolean isRunning() { return scheduler != null; }

    public boolean isGameActive() { return activeGame != null; }

    public String activeGame() { return activeGame; }

    /** Detects running games without starting the watcher (used by the UI). */
    public Optional<String> findRunningGame() {
        return ProcessHandle.allProcesses()
                .map(this::exeName)
                .filter(n -> n != null && games.contains(n))
                .findFirst();
    }

    private void tick() {
        try {
            ProcessHandle found = ProcessHandle.allProcesses()
                    .filter(p -> {
                        String n = exeName(p);
                        return n != null && games.contains(n);
                    })
                    .findFirst().orElse(null);
            if (found != null && activeGame == null) {
                beginSession(exeName(found), found.pid());
            } else if (found == null && activeGame != null) {
                endSession();
            } else if (found != null && found.pid() != activePid) {
                activePid = found.pid();
                if (options.highPriority()) WinNative.setPriorityClass(activePid, WinNative.HIGH_PRIORITY_CLASS);
            }
        } catch (Throwable t) {
            System.err.println("[GameBooster] " + t.getMessage());
        }
    }

    private synchronized void beginSession(String exe, long pid) {
        activeGame = exe;
        activePid = pid;
        StringBuilder summary = new StringBuilder();
        if (options.highPriority() && WinNative.setPriorityClass(pid, WinNative.HIGH_PRIORITY_CLASS)) {
            summary.append("prioridade alta");
        }
        if (options.powerPlan() && !planAction.isApplied(ctx)) {
            Map<String, String> original = planAction.capture(ctx);
            persistSession(original);
            if (planAction.apply(ctx)) {
                savedPlan = original;
                append(summary, "plano Desempenho Máximo");
            } else {
                clearSession();
            }
        }
        if (options.cleanRam()) {
            MemoryService.CleanResult r = memory.clean(MemoryService.CleanMode.LIGHT);
            if (r.success()) append(summary, "RAM otimizada");
        }
        Listener l = listener;
        if (l != null) l.onGameStarted(exe, summary.length() == 0 ? "monitorando" : summary.toString());
    }

    private synchronized void endSession() {
        String exe = activeGame;
        activeGame = null;
        activePid = -1;
        if (savedPlan != null) {
            planAction.revert(ctx, savedPlan);
            savedPlan = null;
        }
        clearSession();
        Listener l = listener;
        if (l != null && exe != null) l.onGameStopped(exe);
    }

    /** Restores the power plan if the app closed while a game session was active. */
    private void recoverStaleSession() {
        try {
            if (!Files.exists(sessionFile)) return;
            @SuppressWarnings("unchecked")
            Map<String, String> original = gson.fromJson(Files.readString(sessionFile, StandardCharsets.UTF_8), HashMap.class);
            if (original != null && findRunningGame().isEmpty()) {
                planAction.revert(ctx, original);
                clearSession();
            } else if (original != null) {
                savedPlan = original;
            }
        } catch (Exception ignored) {}
    }

    private void persistSession(Map<String, String> original) {
        try {
            Files.createDirectories(sessionFile.getParent());
            Files.writeString(sessionFile, gson.toJson(original), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    private void clearSession() {
        try {
            Files.deleteIfExists(sessionFile);
        } catch (Exception ignored) {}
    }

    private String exeName(ProcessHandle p) {
        try {
            return p.info().command()
                    .map(c -> Path.of(c).getFileName().toString().toLowerCase(Locale.ROOT))
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static void append(StringBuilder sb, String text) {
        if (sb.length() > 0) sb.append(", ");
        sb.append(text);
    }
}
