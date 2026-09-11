package com.nextgen.optimizer.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Application-wide settings, serialised to / from JSON via Gson.
 * <p>
 * The default instance uses sensible values so the app starts cleanly
 * even when no settings file exists on disk yet.
 */
public class AppSettings {

    // ── Fields with defaults ────────────────────────────────────────────
    private String language = "pt-BR";
    private boolean overlayEnabled = false;
    private String overlayPosition = "top-left";
    private double overlayOpacity = 0.85;
    private int monitorRefreshRate = 1000;       // ms
    private boolean autoBackup = true;
    private boolean startMinimized = false;
    private boolean startWithWindows = false;
    private String overlayHotkey = "Ctrl+Shift+O";

    /** Shared Gson instance — pretty-printed for human-readable config files. */
    private static final transient Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();

    // ── Persistence ─────────────────────────────────────────────────────

    /**
     * Serialises this settings object to the given path as pretty-printed JSON.
     *
     * @param path destination file; parent directories are created if missing.
     * @return {@code true} on success.
     */
    public boolean save(Path path) {
        try {
            Files.createDirectories(path.getParent());
            String json = GSON.toJson(this);
            Files.writeString(path, json, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            System.err.println("[AppSettings] Failed to save settings to " + path + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Loads an {@code AppSettings} instance from the given JSON file.
     * Returns a default instance if the file is missing or corrupt.
     *
     * @param path source file.
     * @return loaded or default settings.
     */
    public static AppSettings load(Path path) {
        if (!Files.exists(path)) {
            System.out.println("[AppSettings] No settings file found at " + path + ", using defaults.");
            return new AppSettings();
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            AppSettings settings = GSON.fromJson(json, AppSettings.class);
            if (settings == null) {
                return new AppSettings();
            }
            settings.validate();
            return settings;
        } catch (Exception e) {
            System.err.println("[AppSettings] Failed to load settings from " + path + ": " + e.getMessage());
            return new AppSettings();
        }
    }

    /**
     * Clamps / fixes any out-of-range values that could come from a hand-edited file.
     */
    private void validate() {
        if (language == null || language.isBlank()) language = "pt-BR";
        if (overlayPosition == null || !isValidPosition(overlayPosition)) overlayPosition = "top-left";
        overlayOpacity = Math.max(0.5, Math.min(1.0, overlayOpacity));
        monitorRefreshRate = Math.max(250, Math.min(5000, monitorRefreshRate));
        if (overlayHotkey == null || overlayHotkey.isBlank()) overlayHotkey = "Ctrl+Shift+O";
    }

    private static boolean isValidPosition(String pos) {
        return "top-left".equals(pos) || "top-right".equals(pos) ||
               "bottom-left".equals(pos) || "bottom-right".equals(pos);
    }

    // ── Getters / Setters ───────────────────────────────────────────────

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public boolean isOverlayEnabled() {
        return overlayEnabled;
    }

    public void setOverlayEnabled(boolean overlayEnabled) {
        this.overlayEnabled = overlayEnabled;
    }

    public String getOverlayPosition() {
        return overlayPosition;
    }

    public void setOverlayPosition(String overlayPosition) {
        this.overlayPosition = overlayPosition;
    }

    public double getOverlayOpacity() {
        return overlayOpacity;
    }

    public void setOverlayOpacity(double overlayOpacity) {
        this.overlayOpacity = Math.max(0.5, Math.min(1.0, overlayOpacity));
    }

    public int getMonitorRefreshRate() {
        return monitorRefreshRate;
    }

    public void setMonitorRefreshRate(int monitorRefreshRate) {
        this.monitorRefreshRate = Math.max(250, Math.min(5000, monitorRefreshRate));
    }

    public boolean isAutoBackup() {
        return autoBackup;
    }

    public void setAutoBackup(boolean autoBackup) {
        this.autoBackup = autoBackup;
    }

    public boolean isStartMinimized() {
        return startMinimized;
    }

    public void setStartMinimized(boolean startMinimized) {
        this.startMinimized = startMinimized;
    }

    public boolean isStartWithWindows() {
        return startWithWindows;
    }

    public void setStartWithWindows(boolean startWithWindows) {
        this.startWithWindows = startWithWindows;
    }

    public String getOverlayHotkey() {
        return overlayHotkey;
    }

    public void setOverlayHotkey(String overlayHotkey) {
        this.overlayHotkey = overlayHotkey;
    }

    @Override
    public String toString() {
        return GSON.toJson(this);
    }
}
