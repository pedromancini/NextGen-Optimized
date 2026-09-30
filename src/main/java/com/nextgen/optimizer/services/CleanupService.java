package com.nextgen.optimizer.services;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Scan-then-clean disk cleanup. Only caches and temporary data are targeted;
 * files touched recently are skipped, symbolic links are never followed and
 * locked files are left alone.
 */
public class CleanupService {

    public enum Kind { FILES, RECYCLE_BIN, WINDOWS_UPDATE, DELIVERY_OPTIMIZATION }

    public record Target(String id, String title, String description, boolean recommended,
                         Duration minAge, Kind kind, Supplier<List<Path>> paths) {}

    public record ScanResult(Target target, long bytes, long files) {}

    private final PowerShellService shell;
    private final SteamLocator steam;
    private final List<Target> targets;

    public CleanupService(PowerShellService shell, SteamLocator steam) {
        this.shell = shell;
        this.steam = steam;
        this.targets = buildTargets();
    }

    public List<Target> targets() {
        return targets;
    }

    private List<Target> buildTargets() {
        String local = env("LOCALAPPDATA");
        String windir = env("WINDIR", "C:\\Windows");
        String programData = env("ProgramData", "C:\\ProgramData");
        List<Target> list = new ArrayList<>();

        list.add(new Target("user-temp", "Temporários do usuário",
                "Arquivos em %TEMP% deixados por instaladores e programas.", true, Duration.ofHours(2), Kind.FILES,
                () -> List.of(Path.of(System.getProperty("java.io.tmpdir")))));
        list.add(new Target("windows-temp", "Temporários do Windows",
                "Arquivos em C:\\Windows\\Temp.", true, Duration.ofHours(2), Kind.FILES,
                () -> List.of(Path.of(windir, "Temp"))));
        list.add(new Target("error-reports", "Relatórios de erro e dumps",
                "Relatórios de falhas já processados e minidumps com mais de 7 dias.", true, Duration.ofDays(7), Kind.FILES,
                () -> List.of(Path.of(programData, "Microsoft\\Windows\\WER\\ReportArchive"),
                        Path.of(programData, "Microsoft\\Windows\\WER\\ReportQueue"),
                        Path.of(local, "CrashDumps"),
                        Path.of(windir, "Minidump"))));
        list.add(new Target("delivery-optimization", "Cache de Otimização de Entrega",
                "Pedaços de atualizações já instaladas guardados para compartilhar.", true, Duration.ZERO,
                Kind.DELIVERY_OPTIMIZATION,
                () -> List.of(Path.of(windir, "ServiceProfiles\\NetworkService\\AppData\\Local\\Microsoft\\Windows\\DeliveryOptimization\\Cache"))));
        list.add(new Target("windows-logs", "Logs antigos do Windows",
                "Logs de instalação e diagnóstico com mais de 7 dias.", true, Duration.ofDays(7), Kind.FILES,
                () -> List.of(Path.of(windir, "Logs\\CBS"), Path.of(windir, "Logs\\DISM"),
                        Path.of(windir, "Logs\\MoSetup"))));
        list.add(new Target("browser-cache", "Cache dos navegadores",
                "Cache de Chrome, Edge, Brave, Opera e Firefox. Logins e histórico não são tocados.", false,
                Duration.ofMinutes(30), Kind.FILES, () -> browserCaches(local)));
        list.add(new Target("windows-update", "Downloads do Windows Update",
                "Pacotes de atualização já baixados. O serviço é pausado durante a limpeza.", false,
                Duration.ofDays(1), Kind.WINDOWS_UPDATE,
                () -> List.of(Path.of(windir, "SoftwareDistribution\\Download"))));
        list.add(new Target("recycle-bin", "Lixeira", "Esvazia a Lixeira de todas as unidades.", false,
                Duration.ZERO, Kind.RECYCLE_BIN, List::of));
        list.add(new Target("gpu-shader-cache", "Cache de shaders da GPU",
                "DirectX/NVIDIA/AMD. Use após atualizar o driver de vídeo — os jogos recompilam os shaders na primeira partida.",
                false, Duration.ofMinutes(30), Kind.FILES,
                () -> List.of(Path.of(local, "D3DSCache"), Path.of(local, "NVIDIA\\DXCache"), Path.of(local, "NVIDIA\\GLCache"),
                        Path.of(env("USERPROFILE"), "AppData\\LocalLow\\NVIDIA\\PerDriverVersion\\DXCache"),
                        Path.of(local, "AMD\\DxCache"), Path.of(local, "AMD\\DxcCache"), Path.of(local, "AMD\\VkCache"))));
        list.add(new Target("cs2-shader-cache", "Cache de shaders do CS2 (Steam)",
                "Resolve travadas após updates do jogo ou do driver. A primeira partida recompila os shaders.", false,
                Duration.ofMinutes(30), Kind.FILES, () -> steam.shaderCaches(true)));
        return list;
    }

    private static List<Path> browserCaches(String local) {
        List<Path> paths = new ArrayList<>();
        for (String base : List.of("Google\\Chrome\\User Data", "Microsoft\\Edge\\User Data",
                "BraveSoftware\\Brave-Browser\\User Data")) {
            Path root = Path.of(local, base);
            if (!Files.isDirectory(root)) continue;
            try (var profiles = Files.list(root)) {
                profiles.filter(p -> {
                    String n = p.getFileName().toString();
                    return n.equals("Default") || n.startsWith("Profile ");
                }).forEach(p -> {
                    paths.add(p.resolve("Cache"));
                    paths.add(p.resolve("Code Cache"));
                    paths.add(p.resolve("GPUCache"));
                });
            } catch (IOException ignored) {}
        }
        paths.add(Path.of(local, "Opera Software\\Opera Stable\\Cache"));
        Path firefox = Path.of(local, "Mozilla\\Firefox\\Profiles");
        if (Files.isDirectory(firefox)) {
            try (var profiles = Files.list(firefox)) {
                profiles.forEach(p -> paths.add(p.resolve("cache2")));
            } catch (IOException ignored) {}
        }
        return paths;
    }

    // ── Scan ────────────────────────────────────────────────────────────

    public ScanResult scan(Target target) {
        if (target.kind() == Kind.RECYCLE_BIN) {
            String out = shell.executeSync("$s = 0; (New-Object -ComObject Shell.Application).NameSpace(10).Items() | "
                    + "ForEach-Object { $s += $_.Size }; $s");
            return new ScanResult(target, parseLong(out), 0);
        }
        long[] totals = new long[2];
        long cutoff = System.currentTimeMillis() - target.minAge().toMillis();
        for (Path root : target.paths().get()) {
            walk(root, cutoff, false, totals);
        }
        return new ScanResult(target, totals[0], totals[1]);
    }

    // ── Clean ───────────────────────────────────────────────────────────

    /** @return bytes freed */
    public long clean(Target target) {
        long cutoff = System.currentTimeMillis() - target.minAge().toMillis();
        long[] totals = new long[2];
        switch (target.kind()) {
            case RECYCLE_BIN -> {
                long before = scan(target).bytes();
                shell.executeSync("Clear-RecycleBin -Force -ErrorAction SilentlyContinue");
                return Math.max(0, before - scan(target).bytes());
            }
            case DELIVERY_OPTIMIZATION -> {
                long before = scan(target).bytes();
                shell.executeResult("Delete-DeliveryOptimizationCache -Force -ErrorAction SilentlyContinue", 120);
                return Math.max(0, before - scan(target).bytes());
            }
            case WINDOWS_UPDATE -> {
                String state = shell.runProcess(15, "sc.exe", "query", "wuauserv").output();
                boolean wasRunning = state.contains("RUNNING");
                shell.runProcess(60, "net.exe", "stop", "wuauserv");
                try {
                    for (Path root : target.paths().get()) walk(root, cutoff, true, totals);
                } finally {
                    if (wasRunning) shell.runProcess(60, "net.exe", "start", "wuauserv");
                }
                return totals[0];
            }
            default -> {
                for (Path root : target.paths().get()) walk(root, cutoff, true, totals);
                return totals[0];
            }
        }
    }

    /**
     * Walks {@code root} (never the root itself), summing — and optionally
     * deleting — regular files older than {@code cutoff}. Directories emptied
     * by the walk are removed; links and junctions are skipped.
     */
    private static void walk(Path root, long cutoff, boolean delete, long[] totals) {
        if (root == null || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return attrs.isSymbolicLink() || attrs.isOther() ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!attrs.isRegularFile() || attrs.lastModifiedTime().toMillis() > cutoff) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (delete) {
                        try {
                            Files.delete(file);
                        } catch (IOException locked) {
                            return FileVisitResult.CONTINUE;
                        }
                    }
                    totals[0] += attrs.size();
                    totals[1]++;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    if (delete && !dir.equals(root)) {
                        try {
                            Files.delete(dir); // only succeeds when empty
                        } catch (IOException ignored) {}
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {}
    }

    private static long parseLong(String text) {
        try {
            return (long) Double.parseDouble(text.trim().replace(',', '.'));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String env(String name) {
        return env(name, System.getProperty("user.home"));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
