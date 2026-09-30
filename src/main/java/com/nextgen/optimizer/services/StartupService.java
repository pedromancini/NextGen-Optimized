package com.nextgen.optimizer.services;

import com.sun.jna.platform.win32.WinReg;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Startup programs, toggled exactly like Task Manager does: through the
 * {@code StartupApproved} flags. The program's own entry is never deleted, so
 * re-enabling always works.
 */
public class StartupService {

    public record Item(String name, String command, String location, WinReg.HKEY approvedRoot,
                       String approvedKey, boolean enabled, boolean commonlyDisabled) {}

    private static final String RUN = "Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String RUN32 = "SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String APPROVED = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\";
    private static final Set<String> COMMONLY_DISABLED = Set.of("steam", "discord", "spotify", "epic", "teams", "onedrive",
            "skype", "adobe", "ccleaner", "itunes", "battle.net", "riot", "ubisoft", "origin", "ea desktop", "edge", "opera",
            "whatsapp", "telegram", "zoom", "dropbox", "googledrive", "google drive");

    private final RegistryService registry;

    public StartupService(RegistryService registry) {
        this.registry = registry;
    }

    public List<Item> list() {
        List<Item> items = new ArrayList<>();
        addRegistry(items, WinReg.HKEY_CURRENT_USER, RUN, "Usuário atual", "Run");
        addRegistry(items, WinReg.HKEY_LOCAL_MACHINE, RUN, "Todos os usuários", "Run");
        addRegistry(items, WinReg.HKEY_LOCAL_MACHINE, RUN32, "Todos os usuários (32 bits)", "Run32");
        addFolder(items, Path.of(System.getenv().getOrDefault("APPDATA", ""), "Microsoft\\Windows\\Start Menu\\Programs\\Startup"),
                WinReg.HKEY_CURRENT_USER, "Pasta Inicializar");
        addFolder(items, Path.of(System.getenv().getOrDefault("ProgramData", "C:\\ProgramData"), "Microsoft\\Windows\\Start Menu\\Programs\\StartUp"),
                WinReg.HKEY_LOCAL_MACHINE, "Pasta Inicializar (todos)");
        items.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return items;
    }

    private void addRegistry(List<Item> items, WinReg.HKEY root, String path, String location, String approvedSub) {
        for (String name : registry.getValueNames(root, path)) {
            if (name == null || name.isBlank()) continue;
            Object cmd = registry.getRawValue(root, path, name);
            items.add(new Item(name, cmd == null ? "" : cmd.toString(), location, root,
                    APPROVED + approvedSub, isEnabled(root, APPROVED + approvedSub, name), common(name)));
        }
    }

    private void addFolder(List<Item> items, Path folder, WinReg.HKEY root, String location) {
        if (!Files.isDirectory(folder)) return;
        try (var files = Files.list(folder)) {
            files.filter(f -> !f.getFileName().toString().equalsIgnoreCase("desktop.ini")).forEach(f -> {
                String name = f.getFileName().toString();
                items.add(new Item(name, f.toString(), location, root, APPROVED + "StartupFolder",
                        isEnabled(root, APPROVED + "StartupFolder", name), common(name)));
            });
        } catch (Exception ignored) {}
    }

    private boolean isEnabled(WinReg.HKEY root, String key, String name) {
        byte[] data = registry.getBinaryValue(root, key, name);
        return data == null || data.length == 0 || (data[0] & 1) == 0;
    }

    private static boolean common(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return COMMONLY_DISABLED.stream().anyMatch(n::contains);
    }

    public boolean setEnabled(Item item, boolean enabled) {
        byte[] data = new byte[12];
        data[0] = (byte) (enabled ? 0x02 : 0x03);
        if (!enabled) {
            // Task Manager stores the time the item was disabled as a FILETIME.
            long fileTime = (System.currentTimeMillis() + 11644473600000L) * 10_000L;
            ByteBuffer.wrap(data, 4, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(fileTime);
        }
        return registry.setBinaryValue(item.approvedRoot(), item.approvedKey(), item.name(), data);
    }
}
