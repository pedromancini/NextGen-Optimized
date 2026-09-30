package com.nextgen.optimizer.services;

import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.*;

/**
 * Process inspection for the Monitor page: running processes with path,
 * command line and a simple location-based classification.
 */
public class ProcessService {

    private final PowerShellService ps;

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

    public ProcessService(PowerShellService powerShellService) {
        this.ps = powerShellService;
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
            System.err.println("[ProcessService] Error in deep process scan: " + e.getMessage());
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
}
