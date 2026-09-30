package com.nextgen.optimizer.services;

import com.sun.jna.platform.win32.WinReg;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds Steam and Counter-Strike 2 across every Steam library folder instead
 * of assuming the default install path.
 */
public class SteamLocator {

    public static final String CS2_APP_ID = "730";
    private static final String CS2_RELATIVE = "steamapps\\common\\Counter-Strike Global Offensive";
    private static final Pattern LIBRARY_PATH = Pattern.compile("\"path\"\\s+\"([^\"]+)\"");

    private final RegistryService registry;
    private volatile Path cachedCs2Root;
    private volatile boolean resolved;

    public SteamLocator(RegistryService registry) {
        this.registry = registry;
    }

    public Path steamRoot() {
        String path = registry.getStringValue(WinReg.HKEY_CURRENT_USER, "Software\\Valve\\Steam", "SteamPath", "");
        if (path.isBlank()) {
            path = registry.getStringValue(WinReg.HKEY_LOCAL_MACHINE, "SOFTWARE\\WOW6432Node\\Valve\\Steam", "InstallPath", "");
        }
        if (path.isBlank()) {
            Path fallback = Path.of("C:\\Program Files (x86)\\Steam");
            return Files.isDirectory(fallback) ? fallback : null;
        }
        return Path.of(path.replace('/', '\\'));
    }

    public List<Path> libraries() {
        Set<Path> libs = new LinkedHashSet<>();
        Path root = steamRoot();
        if (root == null) return new ArrayList<>();
        libs.add(root);
        Path vdf = root.resolve("steamapps").resolve("libraryfolders.vdf");
        try {
            if (Files.isRegularFile(vdf)) {
                Matcher m = LIBRARY_PATH.matcher(Files.readString(vdf, StandardCharsets.UTF_8));
                while (m.find()) {
                    libs.add(Path.of(m.group(1).replace("\\\\", "\\")));
                }
            }
        } catch (Exception ignored) {}
        return new ArrayList<>(libs);
    }

    /** CS2 install root ("...\\Counter-Strike Global Offensive"), or null when not installed. */
    public synchronized Path cs2Root() {
        if (resolved) return cachedCs2Root;
        for (Path lib : libraries()) {
            Path candidate = lib.resolve(CS2_RELATIVE);
            if (Files.isRegularFile(candidate.resolve("game\\bin\\win64\\cs2.exe"))) {
                cachedCs2Root = candidate;
                break;
            }
        }
        resolved = true;
        return cachedCs2Root;
    }

    public Path cs2Executable() {
        Path root = cs2Root();
        return root == null ? null : root.resolve("game\\bin\\win64\\cs2.exe");
    }

    public Path cs2CfgDir() {
        Path root = cs2Root();
        return root == null ? null : root.resolve("game\\csgo\\cfg");
    }

    /** Shader caches of every library, or only CS2's when {@code cs2Only}. */
    public List<Path> shaderCaches(boolean cs2Only) {
        List<Path> result = new ArrayList<>();
        for (Path lib : libraries()) {
            Path cache = lib.resolve("steamapps").resolve("shadercache");
            result.add(cs2Only ? cache.resolve(CS2_APP_ID) : cache);
        }
        return result;
    }

    public String cs2ExecutableString() {
        Path exe = cs2Executable();
        return exe == null ? null : exe.toString();
    }
}
