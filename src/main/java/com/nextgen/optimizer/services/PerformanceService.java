package com.nextgen.optimizer.services;

import com.sun.jna.platform.win32.WinReg;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * High-level system performance optimizations: power plans, process management,
 * RAM cleanup, temp file deletion, and Game Mode orchestration.
 */
public class PerformanceService {

    private static final String HIGH_PERF_GUID = "8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c";
    private static final String ULTIMATE_PERF_GUID = "e9a42b02-d5df-448d-aa00-03f14749eb61";

    // Background helpers that are reasonable to stop during a game session.
    // Avoid killing user-facing apps such as browsers, chat, music, or cloud sync.
    private static final List<String> DEFAULT_BACKGROUND_PROCESSES = List.of(
            "GameBarPresenceWriter.exe", "XboxGameBar.exe", "XboxPcApp.exe",
            "YourPhone.exe", "PhoneExperienceHost.exe", "Widgets.exe",
            "MicrosoftEdgeUpdate.exe", "AdobeARM.exe", "AdobeCollabSync.exe"
    );

    private final PowerShellService ps;
    private final RegistryService reg;
    private final BackupService backup;

    private final AtomicBoolean gameModeActive = new AtomicBoolean(false);
    private String previousPowerPlanGuid;

    public static class ProcessInfo {
        private final String name;
        private final int pid;
        private final double cpuPercent;
        private final long ramMb;
        private final String path;
        private final String commandLine;
        private final String status;

        public ProcessInfo(String name, int pid, double cpuPercent, long ramMb,
                           String path, String commandLine, String status) {
            this.name = name == null ? "" : name;
            this.pid = pid;
            this.cpuPercent = cpuPercent;
            this.ramMb = ramMb;
            this.path = path == null ? "" : path;
            this.commandLine = commandLine == null ? "" : commandLine;
            this.status = status == null ? "OK" : status;
        }

        public String getName() { return name; }
        public int getPid() { return pid; }
        public double getCpuPercent() { return cpuPercent; }
        public long getRamMb() { return ramMb; }
        public String getPath() { return path; }
        public String getCommandLine() { return commandLine; }
        public String getStatus() { return status; }
    }

    public PerformanceService(PowerShellService powerShellService,
                              RegistryService registryService,
                              BackupService backupService) {
        this.ps = powerShellService;
        this.reg = registryService;
        this.backup = backupService;
    }

    // ═══════════════════════════════════════════════════════════════
    //  GAME MODE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Activate Game Mode — runs ALL optimizations in sequence:
     * high-perf power plan, disable CPU power saving, clear RAM, clear temp, kill bloat.
     */
    public CompletableFuture<Void> activateGameMode() {
        return CompletableFuture.runAsync(() -> {
            try {
                // Save current power plan so we can restore it
                previousPowerPlanGuid = parseActiveGuid(ps.executeSync("powercfg /getactivescheme"));

                // Backup CPU-related registry entries
                BackupService.BackupSnapshot snap = backup.createBackup("game-mode");
                backup.addEntry(snap,
                        "SYSTEM\\CurrentControlSet\\Control\\Power\\PowerSettings\\54533251-82be-4824-96c1-47b60b740d00\\be337238-0d82-4146-a960-4f3749d470c7",
                        "ValueMax", WinReg.HKEY_LOCAL_MACHINE);
                backup.saveBackup(snap);

                // 1. Ultimate Performance power plan (e9a42b02-d5df-448d-aa00-03f14749eb61)
                setUltimatePerformancePlan();

                // 2. Disable CPU power saving (set min processor state to 100%)
                ps.executeSync("powercfg /setacvalueindex SCHEME_CURRENT SUB_PROCESSOR PROCTHROTTLEMIN 100");
                ps.executeSync("powercfg /setactive SCHEME_CURRENT");

                // 3. Clear standby RAM
                clearStandbyRam();

                // 4. Clear temp files
                clearTempFiles();

                // 5. Kill background processes
                killBackgroundProcesses(DEFAULT_BACKGROUND_PROCESSES);

                gameModeActive.set(true);
            } catch (Exception e) {
                System.err.println("[PerformanceService] Error activating game mode: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Deactivate Game Mode — restore previous power plan and backup.
     */
    public CompletableFuture<Void> deactivateGameMode() {
        return CompletableFuture.runAsync(() -> {
            try {
                // Restore previous power plan
                if (previousPowerPlanGuid != null && !previousPowerPlanGuid.isEmpty()) {
                    ps.executeSync("powercfg /setactive " + previousPowerPlanGuid);
                }

                // Restore CPU power saving defaults (reset min processor state to 5%)
                ps.executeSync("powercfg /setacvalueindex SCHEME_CURRENT SUB_PROCESSOR PROCTHROTTLEMIN 5");
                ps.executeSync("powercfg /setactive SCHEME_CURRENT");

                // Restore registry backup
                BackupService.BackupSnapshot snap = backup.loadLatestBackup("game-mode");
                if (snap != null) {
                    backup.restoreBackup(snap);
                }

                gameModeActive.set(false);
            } catch (Exception e) {
                System.err.println("[PerformanceService] Error deactivating game mode: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * @return true if game mode is currently activated
     */
    public boolean isGameModeActive() {
        return gameModeActive.get();
    }

    // ═══════════════════════════════════════════════════════════════
    //  POWER PLANS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Switch to the built-in High Performance power plan.
     */
    public void setHighPerformancePlan() {
        ps.executeSync("powercfg /setactive " + HIGH_PERF_GUID);
    }

    /**
     * Duplicate and activate the Ultimate Performance power plan.
     * On some Windows editions the plan is hidden and must be duplicated first.
     */
    public void setUltimatePerformancePlan() {
        String existingPlans = ps.executeSync("powercfg /list");
        String existingUltimateGuid = findUltimatePerformanceGuid(existingPlans);
        if (existingUltimateGuid != null && !existingUltimateGuid.isBlank()) {
            ps.executeSync("powercfg /setactive " + existingUltimateGuid);
            return;
        }

        // Duplicate creates a copy and prints the new GUID when the plan is hidden.
        String output = ps.executeSync("powercfg -duplicatescheme " + ULTIMATE_PERF_GUID);
        String newGuid = parseActiveGuid(output);
        if (newGuid != null && !newGuid.isEmpty()) {
            ps.executeSync("powercfg /setactive " + newGuid);
        } else {
            // Fallback: try activating the original GUID (works on Win 10 Pro / Win 11 Pro)
            ps.executeSync("powercfg /setactive " + ULTIMATE_PERF_GUID);
        }
    }

    /**
     * @return human-readable description of the active power plan, e.g.
     *         "Power Scheme GUID: 8c5e7fda-...  (High Performance)"
     */
    public String getActivePowerPlan() {
        String output = ps.executeSync("powercfg /getactivescheme");
        if (output == null || output.isBlank()) return "Desconhecido";

        String planName = output.trim();
        int openParen = planName.lastIndexOf('(');
        int closeParen = planName.lastIndexOf(')');
        if (openParen != -1 && closeParen > openParen) {
            planName = planName.substring(openParen + 1, closeParen).trim();
        }

        // Fix encoding glitches (e.g. M?ximo, Mximo, Mximo -> Máximo)
        planName = planName.replaceAll("M[^\\s]*ximo", "Máximo");
        planName = planName.replace("Mximo", "Máximo");

        return planName;
    }

    // ═══════════════════════════════════════════════════════════════
    //  PROCESS MANAGEMENT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Set a running process to a given Windows priority class.
     * <p>Priority values: 64=Idle, 16384=BelowNormal, 32=Normal,
     * 32768=AboveNormal, 128=High, 256=Realtime</p>
     */
    public void setProcessPriority(String processName, int priority) {
        ps.executeSync("wmic process where name=\"" + processName +
                "\" CALL setpriority " + priority);
    }

    /**
     * Set a running process's CPU affinity mask via PowerShell.
     *
     * @param processName the process executable name (e.g. "game.exe")
     * @param affinityMask bitmask of allowed CPUs (e.g. 0xFF for cores 0-7)
     */
    public void setProcessAffinity(String processName, long affinityMask) {
        String cmd = String.format(
                "Get-Process -Name '%s' -ErrorAction SilentlyContinue | " +
                "ForEach-Object { $_.ProcessorAffinity = [IntPtr]%d }",
                processName.replace(".exe", ""), affinityMask);
        ps.executeSync(cmd);
    }

    /**
     * Clear standby RAM (cached pages) using a PowerShell-based approach.
     * Falls back gracefully if EmptyStandbyList.exe is not available.
     */
    public void clearStandbyRam() {
        // Try the well-known EmptyStandbyList utility first
        String result = ps.executeSync("where EmptyStandbyList.exe 2>nul");
        if (result != null && result.contains("EmptyStandbyList")) {
            ps.executeSync("EmptyStandbyList.exe standbylist");
        } else {
            // PowerShell fallback: invoke memory pressure through .NET
            ps.executeSync(
                    "[System.Runtime.GCSettings]::LargeObjectHeapCompactionMode = " +
                    "'CompactOnce'; [System.GC]::Collect(); [System.GC]::WaitForPendingFinalizers();"
            );
            // Also release working sets of all processes
            ps.executeSync(
                    "Get-Process | Where-Object { $_.Id -ne $PID -and $_.ProcessName -ne 'System' -and " +
                    "$_.ProcessName -ne 'Idle' } | ForEach-Object { " +
                    "try { $_.MinWorkingSet = [IntPtr]::op_Explicit(1MB) } catch {} }"
            );
        }
    }

    /**
     * Delete temporary files from user temp and Windows temp directories.
     *
     * @return approximate number of bytes cleaned
     */
    public long clearTempFiles() {
        String script =
                "$ErrorActionPreference = 'SilentlyContinue'; " +
                "$totalFreed = 0; " +
                "$cutoff = (Get-Date).AddMinutes(-15); " +
                "$dirs = @($env:TEMP, 'C:\\Windows\\Temp') | Where-Object { $_ -and (Test-Path -LiteralPath $_) }; " +
                "foreach ($dir in $dirs) { " +
                "  $items = Get-ChildItem -LiteralPath $dir -Recurse -Force -ErrorAction SilentlyContinue | Sort-Object FullName -Descending; " +
                "  foreach ($item in $items) { " +
                "    if ($item.LastWriteTime -le $cutoff) { " +
                "      try { " +
                "        $size = if ($item.PSIsContainer) { 0 } else { $item.Length }; " +
                "        Remove-Item -LiteralPath $item.FullName -Force -Recurse -ErrorAction Stop; " +
                "        $totalFreed += $size " +
                "      } catch {} " +
                "    } " +
                "  } " +
                "} " +
                "Write-Output $totalFreed";
        PowerShellService.CommandResult result = ps.executeResult(script, 90);
        try {
            String output = result.output();
            return Long.parseLong(output.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Kill a list of processes by name.
     */
    public void killBackgroundProcesses(List<String> processNames) {
        for (String name : processNames) {
            ps.executeSync("taskkill /F /IM \"" + name + "\" 2>nul");
        }
    }

    /**
     * Get a list of currently running processes.
     *
     * @return list of [name, PID, CPU%, RAM_MB] string arrays
     */
    public List<String[]> getRunningProcesses() {
        List<String[]> result = new ArrayList<>();
        try {
            SystemInfo si = new SystemInfo();
            OperatingSystem os = si.getOperatingSystem();
            List<OSProcess> procs = os.getProcesses(
                    OperatingSystem.ProcessFiltering.ALL_PROCESSES,
                    OperatingSystem.ProcessSorting.CPU_DESC,
                    150
            );
            Set<String> seenNames = new HashSet<>();
            for (OSProcess p : procs) {
                String name = p.getName();
                if (name == null || name.isBlank() || name.equalsIgnoreCase("System") || name.equalsIgnoreCase("Idle")) {
                    continue;
                }
                if (seenNames.add(name.toLowerCase())) {
                    long ramMb = p.getResidentSetSize() / (1024 * 1024);
                    result.add(new String[]{
                            name,
                            String.valueOf(p.getProcessID()),
                            String.format(Locale.US, "%.1f", p.getProcessCpuLoadCumulative() * 100.0),
                            String.valueOf(ramMb)
                    });
                }
            }
            result.sort(Comparator.comparing(a -> a[0].toLowerCase()));
        } catch (Exception e) {
            System.err.println("[PerformanceService] Error getting running processes: " + e.getMessage());
        }
        return result;
    }

    public List<ProcessInfo> getDeepRunningProcesses(String filter, int limit) {
        Map<Integer, String[]> wmiDetails = loadProcessDetailsFromWmi();
        List<ProcessInfo> result = new ArrayList<>();
        String normalizedFilter = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);

        try {
            SystemInfo si = new SystemInfo();
            OperatingSystem os = si.getOperatingSystem();
            List<OSProcess> procs = os.getProcesses(
                    OperatingSystem.ProcessFiltering.ALL_PROCESSES,
                    OperatingSystem.ProcessSorting.CPU_DESC,
                    Math.max(limit * 3, 250)
            );

            for (OSProcess p : procs) {
                String name = safe(p.getName());
                int pid = p.getProcessID();
                long ramMb = Math.max(0, p.getResidentSetSize() / (1024 * 1024));
                double cpu = Math.max(0, p.getProcessCpuLoadCumulative() * 100.0);

                String path = safe(p.getPath());
                String commandLine = safe(p.getCommandLine());
                String[] details = wmiDetails.get(pid);
                if (details != null) {
                    if (path.isBlank()) path = details[0];
                    if (commandLine.isBlank()) commandLine = details[1];
                }

                String haystack = (name + " " + pid + " " + path + " " + commandLine).toLowerCase(Locale.ROOT);
                if (!normalizedFilter.isBlank() && !haystack.contains(normalizedFilter)) {
                    continue;
                }

                result.add(new ProcessInfo(name, pid, cpu, ramMb, path, commandLine, classifyProcess(name, path, commandLine)));
                if (result.size() >= limit) {
                    break;
                }
            }

            result.sort(Comparator
                    .comparingDouble(ProcessInfo::getCpuPercent).reversed()
                    .thenComparing(Comparator.comparingLong(ProcessInfo::getRamMb).reversed()));
        } catch (Exception e) {
            System.err.println("[PerformanceService] Error in deep process scan: " + e.getMessage());
        }

        return result;
    }

    public boolean terminateProcess(int pid) {
        if (pid <= 4) {
            return false;
        }
        PowerShellService.CommandResult result = ps.executeResult(
                "Stop-Process -Id " + pid + " -Force -ErrorAction Stop",
                15
        );
        return result.isSuccess();
    }

    public boolean openProcessLocation(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String escaped = path.replace("'", "''");
        PowerShellService.CommandResult result = ps.executeResult(
                "if (Test-Path -LiteralPath '" + escaped + "') { explorer.exe /select, '" + escaped + "' }",
                10
        );
        return result.isSuccess();
    }

    /**
     * Restart Windows Explorer (taskbar / desktop shell).
     */
    public void restartExplorer() {
        ps.executeSync("taskkill /F /IM explorer.exe");
        // Small delay to allow full shutdown
        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        ps.executeSync("start explorer.exe");
    }

    private Map<Integer, String[]> loadProcessDetailsFromWmi() {
        Map<Integer, String[]> details = new HashMap<>();
        String script = "Get-CimInstance Win32_Process | ForEach-Object { " +
                "($_.ProcessId.ToString() + \"`t\" + [string]$_.ExecutablePath + \"`t\" + ([string]$_.CommandLine -replace \"`r|`n|`t\", \" \")) " +
                "}";
        PowerShellService.CommandResult result = ps.executeResult(script, 60);
        if (result.output().isBlank()) {
            return details;
        }

        for (String line : result.output().split("\\R")) {
            String[] parts = line.split("\\t", 3);
            if (parts.length >= 1) {
                try {
                    int pid = Integer.parseInt(parts[0].trim());
                    String path = parts.length >= 2 ? parts[1].trim() : "";
                    String commandLine = parts.length >= 3 ? parts[2].trim() : "";
                    details.put(pid, new String[]{path, commandLine});
                } catch (NumberFormatException ignored) {}
            }
        }
        return details;
    }

    private String classifyProcess(String name, String path, String commandLine) {
        String lowerPath = safe(path).toLowerCase(Locale.ROOT);
        String lowerCmd = safe(commandLine).toLowerCase(Locale.ROOT);
        String lowerName = safe(name).toLowerCase(Locale.ROOT);

        if (lowerPath.isBlank()) {
            if (lowerName.equals("system") || lowerName.equals("idle") || lowerName.startsWith("registry")) {
                return "Sistema";
            }
            return "Sem caminho";
        }
        if (lowerPath.contains("\\appdata\\local\\temp\\") || lowerPath.contains("\\windows\\temp\\")) {
            return "Atenção: Temp";
        }
        if ((lowerName.endsWith(".exe") || lowerCmd.contains(".exe")) &&
                !lowerPath.contains("\\program files") &&
                !lowerPath.contains("\\program files (x86)") &&
                !lowerPath.contains("\\windows\\system32") &&
                !lowerPath.contains("\\windows\\syswow64") &&
                !lowerPath.contains("\\users\\")) {
            return "Verificar";
        }
        return "OK";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    // ═══════════════════════════════════════════════════════════════
    //  STARTUP APPS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get the list of startup applications from the registry Run key.
     *
     * @return list of startup app names
     */
    public List<String> getStartupApps() {
        Set<String> appSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        String path = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run";

        String[] userApps = reg.getValueNames(WinReg.HKEY_CURRENT_USER, path);
        for (String s : userApps) {
            if (s != null && !s.trim().isEmpty()) {
                appSet.add(s.trim());
            }
        }

        try {
            String[] machineApps = reg.getValueNames(WinReg.HKEY_LOCAL_MACHINE, path);
            for (String s : machineApps) {
                if (s != null && !s.trim().isEmpty()) {
                    appSet.add(s.trim());
                }
            }
        } catch (Exception ignored) {
            // May require admin rights
        }

        return new ArrayList<>(appSet);
    }

    public boolean isStartupAppEnabled(String appName) {
        String approvedPath = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\Run";
        byte[] hkcuApproved = reg.getBinaryValue(WinReg.HKEY_CURRENT_USER, approvedPath, appName);
        if (hkcuApproved != null && hkcuApproved.length > 0) {
            return (hkcuApproved[0] & 1) == 0;
        }
        byte[] hklmApproved = reg.getBinaryValue(WinReg.HKEY_LOCAL_MACHINE, approvedPath, appName);
        if (hklmApproved != null && hklmApproved.length > 0) {
            return (hklmApproved[0] & 1) == 0;
        }
        return true;
    }

    public void setStartupAppEnabled(String appName, boolean enable) {
        String approvedPath = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\Run";
        if (enable) {
            byte[] enabledData = new byte[]{0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
            reg.setBinaryValue(WinReg.HKEY_CURRENT_USER, approvedPath, appName, enabledData);
        } else {
            byte[] disabledData = new byte[]{0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
            reg.setBinaryValue(WinReg.HKEY_CURRENT_USER, approvedPath, appName, disabledData);
        }
    }

    /**
     * Disable known non-essential startup apps by removing them from the Run key.
     * Only affects HKCU (no admin rights needed).
     */
    public void disableStartupApps() {
        String path = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run";
        List<String> nonEssential = List.of(
                "OneDrive", "Spotify", "Discord", "Steam", "EpicGamesLauncher",
                "iTunesHelper", "AdobeAAMUpdater", "Skype", "Teams"
        );

        String[] currentApps = reg.getValueNames(WinReg.HKEY_CURRENT_USER, path);
        for (String app : currentApps) {
            for (String target : nonEssential) {
                if (app.toLowerCase().contains(target.toLowerCase())) {
                    // Backup first
                    BackupService.BackupSnapshot snap = backup.createBackup("startup-" + app);
                    backup.addEntry(snap, path, app, WinReg.HKEY_CURRENT_USER);
                    backup.saveBackup(snap);
                    // Delete
                    reg.deleteValue(WinReg.HKEY_CURRENT_USER, path, app);
                    break;
                }
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────

    /**
     * Parse a GUID from powercfg output (looks for 8-4-4-4-12 hex pattern).
     */
    private String parseActiveGuid(String output) {
        if (output == null) return null;
        // Pattern: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                .matcher(output);
        return m.find() ? m.group() : null;
    }

    private String findUltimatePerformanceGuid(String output) {
        if (output == null || output.isBlank()) return null;
        java.util.regex.Pattern linePattern = java.util.regex.Pattern.compile(
                "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}).*(Ultimate|Desempenho M[aá]ximo|Maximo)",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = linePattern.matcher(output);
        return matcher.find() ? matcher.group(1) : null;
    }
}
