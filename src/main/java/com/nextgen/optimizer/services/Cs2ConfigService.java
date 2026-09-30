package com.nextgen.optimizer.services;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages a clearly delimited NextGen X block inside the player's CS2
 * {@code autoexec.cfg}. Everything outside the block is preserved and the
 * previous file is backed up before every write.
 */
public class Cs2ConfigService {

    public static final String BLOCK_START = "// >>> NextGen X — bloco gerenciado (edite fora deste bloco)";
    public static final String BLOCK_END = "// <<< NextGen X";
    public static final String LAUNCH_OPTIONS = "+exec autoexec";

    public record Settings(int fpsMax, int fpsMaxMenu, boolean lowLatencySleep, boolean maxRate, boolean telemetryHud) {}

    public record Result(boolean success, String message, Path file) {}

    private final SteamLocator steam;

    public Cs2ConfigService(SteamLocator steam) {
        this.steam = steam;
    }

    public List<String> buildLines(Settings s) {
        List<String> lines = new ArrayList<>();
        lines.add("fps_max " + Math.max(0, s.fpsMax()));
        lines.add("fps_max_ui " + Math.max(30, s.fpsMaxMenu()));
        if (s.lowLatencySleep()) lines.add("engine_low_latency_sleep_after_client_tick true");
        if (s.maxRate()) lines.add("rate 786432");
        if (s.telemetryHud()) {
            lines.add("cl_hud_telemetry_frametime_show 2");
            lines.add("cl_hud_telemetry_ping_show 2");
            lines.add("cl_hud_telemetry_net_misdelivery_show 2");
        }
        lines.add("echo \"NextGen X: autoexec carregado\"");
        return lines;
    }

    public Result write(Settings settings) {
        Path dir = steam.cs2CfgDir();
        if (dir == null || !Files.isDirectory(dir)) {
            return new Result(false, "CS2 não encontrado nas bibliotecas da Steam.", null);
        }
        Path file = dir.resolve("autoexec.cfg");
        try {
            String existing = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
            if (Files.exists(file)) {
                String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                Files.copy(file, dir.resolve("autoexec.cfg.nextgen-" + stamp + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            }
            String updated = mergeBlock(existing, buildLines(settings));
            Path tmp = dir.resolve("autoexec.cfg.tmp");
            Files.writeString(tmp, updated, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            return new Result(true, "autoexec.cfg atualizado. Suas linhas foram preservadas.", file);
        } catch (Exception e) {
            return new Result(false, "Falha ao gravar autoexec.cfg: " + e.getMessage(), file);
        }
    }

    public Result removeBlock() {
        Path dir = steam.cs2CfgDir();
        if (dir == null) return new Result(false, "CS2 não encontrado.", null);
        Path file = dir.resolve("autoexec.cfg");
        try {
            if (!Files.exists(file)) return new Result(true, "Nada para remover.", file);
            String existing = Files.readString(file, StandardCharsets.UTF_8);
            Files.writeString(file, mergeBlock(existing, null), StandardCharsets.UTF_8);
            return new Result(true, "Bloco do NextGen X removido do autoexec.cfg.", file);
        } catch (Exception e) {
            return new Result(false, "Falha ao editar autoexec.cfg: " + e.getMessage(), file);
        }
    }

    /**
     * Replaces (or appends) the managed block. {@code lines == null} removes it.
     * Package-visible for tests.
     */
    public static String mergeBlock(String existing, List<String> lines) {
        String text = existing == null ? "" : existing.replace("\r\n", "\n");
        int start = text.indexOf(BLOCK_START);
        int end = start >= 0 ? text.indexOf(BLOCK_END, start) : -1;
        String before = text;
        String after = "";
        if (start >= 0 && end >= 0) {
            before = text.substring(0, start);
            after = text.substring(end + BLOCK_END.length());
            if (after.startsWith("\n")) after = after.substring(1);
        }
        StringBuilder sb = new StringBuilder(before);
        if (lines != null) {
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
            sb.append(BLOCK_START).append('\n');
            for (String line : lines) sb.append(line).append('\n');
            sb.append(BLOCK_END).append('\n');
        }
        sb.append(after);
        return sb.toString().replace("\n", "\r\n");
    }
}
