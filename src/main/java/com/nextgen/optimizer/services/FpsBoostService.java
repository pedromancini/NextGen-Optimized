package com.nextgen.optimizer.services;

import com.sun.jna.platform.win32.WinReg;

/**
 * FPS-boost tweaks: disabling visual effects, Game Bar, DVR, animations,
 * transparency, mouse acceleration, and Windows Game Mode.
 * <p>
 * Each tweak has an "is…" getter and a "set…" mutator. All registry writes
 * are backed up before modification.
 */
public class FpsBoostService {

    // ─── Registry paths ──────────────────────────────────────────
    private static final WinReg.HKEY HKCU = WinReg.HKEY_CURRENT_USER;

    private static final String GAME_DVR_PATH     = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\GameDVR";
    private static final String GAME_CONFIG_PATH   = "System\\GameConfigStore";
    private static final String PERSONALIZE_PATH   = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize";
    private static final String GAME_BAR_PATH      = "SOFTWARE\\Microsoft\\GameBar";
    private static final String DESKTOP_PATH       = "Control Panel\\Desktop\\WindowMetrics";
    private static final String VISUAL_FX_PATH     = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\VisualEffects";
    private static final String MOUSE_PATH         = "Control Panel\\Mouse";
    private static final String GRAPHICS_DRIVERS_PATH = "SYSTEM\\CurrentControlSet\\Control\\GraphicsDrivers";
    private static final String DWM_PATH              = "SOFTWARE\\Microsoft\\Windows\\Dwm";
    private static final String MULTIMEDIA_PATH       = "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Multimedia\\SystemProfile";
    private static final String GAMES_PROFILE_PATH    = MULTIMEDIA_PATH + "\\Tasks\\Games";

    // Total number of optimizations managed by this service
    private static final int TOTAL_OPTIMIZATIONS = 8;

    private final RegistryService reg;
    private final BackupService backup;

    public FpsBoostService(RegistryService registryService, BackupService backupService) {
        this.reg = registryService;
        this.backup = backupService;
    }

    // ═══════════════════════════════════════════════════════════════
    //  1. Game Bar  (AppCaptureEnabled)
    // ═══════════════════════════════════════════════════════════════

    /**
     * @return true if Game Bar is disabled (AppCaptureEnabled == 0)
     */
    public boolean isGameBarDisabled() {
        return reg.getIntValue(HKCU, GAME_DVR_PATH, "AppCaptureEnabled", 1) == 0;
    }

    /**
     * Enable or disable Game Bar.
     *
     * @param disabled true to disable, false to enable
     */
    public void setGameBarDisabled(boolean disabled) {
        backupKey(GAME_DVR_PATH, "AppCaptureEnabled", "game-bar");
        reg.setIntValue(HKCU, GAME_DVR_PATH, "AppCaptureEnabled", disabled ? 0 : 1);
    }

    // ═══════════════════════════════════════════════════════════════
    //  2. Game DVR  (GameDVR_Enabled)
    // ═══════════════════════════════════════════════════════════════

    /**
     * @return true if Game DVR recording is disabled
     */
    public boolean isGameDvrDisabled() {
        return reg.getIntValue(HKCU, GAME_CONFIG_PATH, "GameDVR_Enabled", 1) == 0;
    }

    public void setGameDvrDisabled(boolean disabled) {
        backupKey(GAME_CONFIG_PATH, "GameDVR_Enabled", "game-dvr");
        reg.setIntValue(HKCU, GAME_CONFIG_PATH, "GameDVR_Enabled", disabled ? 0 : 1);
    }

    // ═══════════════════════════════════════════════════════════════
    //  3. Transparency  (EnableTransparency)
    // ═══════════════════════════════════════════════════════════════

    /**
     * @return true if window transparency effects are disabled
     */
    public boolean isTransparencyDisabled() {
        return reg.getIntValue(HKCU, PERSONALIZE_PATH, "EnableTransparency", 1) == 0;
    }

    public void setTransparencyDisabled(boolean disabled) {
        backupKey(PERSONALIZE_PATH, "EnableTransparency", "transparency");
        reg.setIntValue(HKCU, PERSONALIZE_PATH, "EnableTransparency", disabled ? 0 : 1);
    }

    // ═══════════════════════════════════════════════════════════════
    //  4. Windows Game Mode  (AutoGameModeEnabled)
    // ═══════════════════════════════════════════════════════════════

    /**
     * @return true if Windows Game Mode is enabled (AutoGameModeEnabled == 1)
     */
    public boolean isGameModeEnabled() {
        return reg.getIntValue(HKCU, GAME_BAR_PATH, "AutoGameModeEnabled", 0) == 1;
    }

    public void setGameModeEnabled(boolean enabled) {
        backupKey(GAME_BAR_PATH, "AutoGameModeEnabled", "win-game-mode");
        reg.setIntValue(HKCU, GAME_BAR_PATH, "AutoGameModeEnabled", enabled ? 1 : 0);
    }

    // ═══════════════════════════════════════════════════════════════
    //  5. Animations  (MinAnimate)
    // ═══════════════════════════════════════════════════════════════

    /**
     * @return true if window minimize/maximize animations are disabled
     */
    public boolean isAnimationsDisabled() {
        String val = reg.getStringValue(HKCU, DESKTOP_PATH, "MinAnimate", "1");
        return "0".equals(val);
    }

    public void setAnimationsDisabled(boolean disabled) {
        backupStringKey(DESKTOP_PATH, "MinAnimate", "animations");
        reg.setStringValue(HKCU, DESKTOP_PATH, "MinAnimate", disabled ? "0" : "1");
    }

    // ═══════════════════════════════════════════════════════════════
    //  6. Visual Effects  (VisualFXSetting)
    // ═══════════════════════════════════════════════════════════════

    /**
     * @return true if visual effects are set to "Best performance" (value == 2)
     */
    public boolean isVisualEffectsOptimized() {
        return reg.getIntValue(HKCU, VISUAL_FX_PATH, "VisualFXSetting", 0) == 2;
    }

    public void setVisualEffectsOptimized(boolean optimized) {
        backupKey(VISUAL_FX_PATH, "VisualFXSetting", "visual-fx");
        // 0 = Let Windows decide, 1 = Best appearance, 2 = Best performance, 3 = Custom
        reg.setIntValue(HKCU, VISUAL_FX_PATH, "VisualFXSetting", optimized ? 2 : 0);
    }

    // ═══════════════════════════════════════════════════════════════
    //  7. Mouse Acceleration  (MouseSpeed)
    // ═══════════════════════════════════════════════════════════════

    /**
     * @return true if mouse acceleration / enhance pointer precision is disabled
     */
    public boolean isMouseAccelerationDisabled() {
        String val = reg.getStringValue(HKCU, MOUSE_PATH, "MouseSpeed", "1");
        return "0".equals(val);
    }

    public void setMouseAccelerationDisabled(boolean disabled) {
        backupStringKey(MOUSE_PATH, "MouseSpeed", "mouse-accel");
        backupStringKey(MOUSE_PATH, "MouseThreshold1", "mouse-threshold-1");
        backupStringKey(MOUSE_PATH, "MouseThreshold2", "mouse-threshold-2");
        reg.setStringValue(HKCU, MOUSE_PATH, "MouseSpeed", disabled ? "0" : "1");
        if (disabled) {
            // Also set the threshold values to 0 for a true raw input feel
            reg.setStringValue(HKCU, MOUSE_PATH, "MouseThreshold1", "0");
            reg.setStringValue(HKCU, MOUSE_PATH, "MouseThreshold2", "0");
        } else {
            reg.setStringValue(HKCU, MOUSE_PATH, "MouseThreshold1", "6");
            reg.setStringValue(HKCU, MOUSE_PATH, "MouseThreshold2", "10");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  8. Competitive latency pack (HAGS, MPO, fullscreen and MMCSS)
    // ═══════════════════════════════════════════════════════════════

    public boolean isHardwareGpuSchedulingEnabled() {
        return reg.getIntValue(WinReg.HKEY_LOCAL_MACHINE, GRAPHICS_DRIVERS_PATH, "HwSchMode", 0) == 2;
    }

    public boolean isMpoDisabled() {
        return reg.getIntValue(WinReg.HKEY_LOCAL_MACHINE, DWM_PATH, "OverlayTestMode", 0) == 5;
    }

    public boolean isGameDvrFullscreenOptimized() {
        return reg.getIntValue(HKCU, GAME_CONFIG_PATH, "GameDVR_FSEBehaviorMode", 0) == 2
                && reg.getIntValue(HKCU, GAME_CONFIG_PATH, "GameDVR_HonorUserFSEBehaviorMode", 0) == 1
                && reg.getIntValue(HKCU, GAME_CONFIG_PATH, "GameDVR_DXGIHonorFSEWindowsCompatible", 0) == 1
                && reg.getIntValue(HKCU, GAME_CONFIG_PATH, "GameDVR_EFSEFeatureFlags", 1) == 0;
    }

    public boolean isMultimediaGameProfileOptimized() {
        return reg.getIntValue(WinReg.HKEY_LOCAL_MACHINE, MULTIMEDIA_PATH, "SystemResponsiveness", 20) == 0
                && reg.getIntValue(WinReg.HKEY_LOCAL_MACHINE, GAMES_PROFILE_PATH, "GPU Priority", 0) == 8
                && reg.getIntValue(WinReg.HKEY_LOCAL_MACHINE, GAMES_PROFILE_PATH, "Priority", 0) == 6
                && "High".equalsIgnoreCase(reg.getStringValue(WinReg.HKEY_LOCAL_MACHINE, GAMES_PROFILE_PATH, "Scheduling Category", ""))
                && "High".equalsIgnoreCase(reg.getStringValue(WinReg.HKEY_LOCAL_MACHINE, GAMES_PROFILE_PATH, "SFIO Priority", ""));
    }

    public boolean isCompetitiveLatencyPackApplied() {
        return isHardwareGpuSchedulingEnabled()
                && isMpoDisabled()
                && isGameDvrFullscreenOptimized()
                && isMultimediaGameProfileOptimized();
    }

    public boolean applyCompetitiveLatencyPack() {
        BackupService.BackupSnapshot snap = backup.createBackup("competitive-latency-pack");
        backup.addEntry(snap, GRAPHICS_DRIVERS_PATH, "HwSchMode", WinReg.HKEY_LOCAL_MACHINE);
        backup.addEntry(snap, DWM_PATH, "OverlayTestMode", WinReg.HKEY_LOCAL_MACHINE);
        backup.addEntry(snap, GAME_CONFIG_PATH, "GameDVR_FSEBehaviorMode", HKCU);
        backup.addEntry(snap, GAME_CONFIG_PATH, "GameDVR_HonorUserFSEBehaviorMode", HKCU);
        backup.addEntry(snap, GAME_CONFIG_PATH, "GameDVR_DXGIHonorFSEWindowsCompatible", HKCU);
        backup.addEntry(snap, GAME_CONFIG_PATH, "GameDVR_EFSEFeatureFlags", HKCU);
        backup.addEntry(snap, MULTIMEDIA_PATH, "SystemResponsiveness", WinReg.HKEY_LOCAL_MACHINE);
        backup.addEntry(snap, GAMES_PROFILE_PATH, "GPU Priority", WinReg.HKEY_LOCAL_MACHINE);
        backup.addEntry(snap, GAMES_PROFILE_PATH, "Priority", WinReg.HKEY_LOCAL_MACHINE);
        backup.addEntry(snap, GAMES_PROFILE_PATH, "Scheduling Category", WinReg.HKEY_LOCAL_MACHINE);
        backup.addEntry(snap, GAMES_PROFILE_PATH, "SFIO Priority", WinReg.HKEY_LOCAL_MACHINE);
        backup.saveBackup(snap);

        boolean ok = true;
        ok &= reg.setIntValue(WinReg.HKEY_LOCAL_MACHINE, GRAPHICS_DRIVERS_PATH, "HwSchMode", 2);
        ok &= reg.setIntValue(WinReg.HKEY_LOCAL_MACHINE, DWM_PATH, "OverlayTestMode", 5);
        ok &= reg.setIntValue(HKCU, GAME_CONFIG_PATH, "GameDVR_FSEBehaviorMode", 2);
        ok &= reg.setIntValue(HKCU, GAME_CONFIG_PATH, "GameDVR_HonorUserFSEBehaviorMode", 1);
        ok &= reg.setIntValue(HKCU, GAME_CONFIG_PATH, "GameDVR_DXGIHonorFSEWindowsCompatible", 1);
        ok &= reg.setIntValue(HKCU, GAME_CONFIG_PATH, "GameDVR_EFSEFeatureFlags", 0);
        ok &= reg.setIntValue(WinReg.HKEY_LOCAL_MACHINE, MULTIMEDIA_PATH, "SystemResponsiveness", 0);
        ok &= reg.setIntValue(WinReg.HKEY_LOCAL_MACHINE, GAMES_PROFILE_PATH, "GPU Priority", 8);
        ok &= reg.setIntValue(WinReg.HKEY_LOCAL_MACHINE, GAMES_PROFILE_PATH, "Priority", 6);
        ok &= reg.setStringValue(WinReg.HKEY_LOCAL_MACHINE, GAMES_PROFILE_PATH, "Scheduling Category", "High");
        ok &= reg.setStringValue(WinReg.HKEY_LOCAL_MACHINE, GAMES_PROFILE_PATH, "SFIO Priority", "High");
        return ok;
    }

    public void restoreCompetitiveLatencyPack() {
        BackupService.BackupSnapshot snap = backup.loadLatestBackup("competitive-latency-pack");
        if (snap != null) {
            backup.restoreBackup(snap);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  BULK OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Apply ALL FPS-boost optimizations at once.
     * Creates a combined backup first.
     *
     * @return true when every registry write succeeds.
     */
    public boolean applyAll() {
        BackupService.BackupSnapshot snap = backup.createBackup("fps-boost-all");

        // Backup all values
        backup.addEntry(snap, GAME_DVR_PATH, "AppCaptureEnabled", HKCU);
        backup.addEntry(snap, GAME_CONFIG_PATH, "GameDVR_Enabled", HKCU);
        backup.addEntry(snap, PERSONALIZE_PATH, "EnableTransparency", HKCU);
        backup.addEntry(snap, GAME_BAR_PATH, "AutoGameModeEnabled", HKCU);
        backup.addEntry(snap, DESKTOP_PATH, "MinAnimate", HKCU);
        backup.addEntry(snap, VISUAL_FX_PATH, "VisualFXSetting", HKCU);
        backup.addEntry(snap, MOUSE_PATH, "MouseSpeed", HKCU);
        backup.addEntry(snap, MOUSE_PATH, "MouseThreshold1", HKCU);
        backup.addEntry(snap, MOUSE_PATH, "MouseThreshold2", HKCU);
        backup.saveBackup(snap);

        // Apply all optimizations
        boolean ok = true;
        ok &= reg.setIntValue(HKCU, GAME_DVR_PATH, "AppCaptureEnabled", 0);        // disable game bar
        ok &= reg.setIntValue(HKCU, GAME_CONFIG_PATH, "GameDVR_Enabled", 0);       // disable DVR
        ok &= reg.setIntValue(HKCU, PERSONALIZE_PATH, "EnableTransparency", 0);    // disable transparency
        ok &= reg.setIntValue(HKCU, GAME_BAR_PATH, "AutoGameModeEnabled", 1);      // enable game mode
        ok &= reg.setStringValue(HKCU, DESKTOP_PATH, "MinAnimate", "0");           // disable animations
        ok &= reg.setIntValue(HKCU, VISUAL_FX_PATH, "VisualFXSetting", 2);         // best performance
        ok &= reg.setStringValue(HKCU, MOUSE_PATH, "MouseSpeed", "0");             // disable mouse accel
        ok &= reg.setStringValue(HKCU, MOUSE_PATH, "MouseThreshold1", "0");
        ok &= reg.setStringValue(HKCU, MOUSE_PATH, "MouseThreshold2", "0");
        ok &= applyCompetitiveLatencyPack();
        return ok;
    }

    /**
     * Restore all FPS-boost settings from the most recent "fps-boost-all" backup.
     */
    public void restoreAll() {
        BackupService.BackupSnapshot snap = backup.loadLatestBackup("fps-boost-all");
        if (snap != null) {
            backup.restoreBackup(snap);
        }
    }

    /**
     * Count how many optimizations are currently applied.
     *
     * @return int[]{appliedCount, totalCount}
     */
    public int[] getOptimizationCount() {
        int applied = 0;

        if (isGameBarDisabled()) applied++;
        if (isGameDvrDisabled()) applied++;
        if (isTransparencyDisabled()) applied++;
        if (isGameModeEnabled()) applied++;
        if (isAnimationsDisabled()) applied++;
        if (isVisualEffectsOptimized()) applied++;
        if (isMouseAccelerationDisabled()) applied++;
        if (isCompetitiveLatencyPackApplied()) applied++;

        return new int[]{applied, TOTAL_OPTIMIZATIONS};
    }

    // ─── Internal backup helpers ─────────────────────────────────

    private void backupKey(String path, String name, String label) {
        BackupService.BackupSnapshot snap = backup.createBackup("fps-" + label);
        backup.addEntry(snap, path, name, HKCU);
        backup.saveBackup(snap);
    }

    private void backupStringKey(String path, String name, String label) {
        BackupService.BackupSnapshot snap = backup.createBackup("fps-" + label);
        backup.addEntry(snap, path, name, HKCU);
        backup.saveBackup(snap);
    }
}
