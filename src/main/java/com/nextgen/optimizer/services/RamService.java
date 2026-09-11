package com.nextgen.optimizer.services;

import java.io.File;

/**
 * Service for RAM monitoring and Standby memory clearing.
 */
public class RamService {

    private final PowerShellService powerShellService;
    private final SystemInfoService systemInfoService;

    public RamService(PowerShellService powerShellService, SystemInfoService systemInfoService) {
        this.powerShellService = powerShellService;
        this.systemInfoService = systemInfoService;
    }

    public long getRamTotal() {
        return Runtime.getRuntime().totalMemory();
    }

    public long getRamUsed() {
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    public long getRamAvailable() {
        return Runtime.getRuntime().freeMemory();
    }

    public double getRamUsagePercent() {
        if (systemInfoService != null && systemInfoService.getLatestSnapshot() != null) {
            return systemInfoService.getLatestSnapshot().getRamUsagePercent();
        }
        return 50.0;
    }

    public boolean clearStandbyMemory() {
        try {
            File utility = new File("tools/EmptyStandbyList.exe");
            if (utility.exists()) {
                powerShellService.executeSync("& '" + utility.getAbsolutePath() + "' standbylist");
                return true;
            } else {
                powerShellService.executeSync("[GC]::Collect(); [GC]::WaitForPendingFinalizers(); [GC]::Collect()");
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public boolean clearWorkingSets() {
        try {
            File utility = new File("tools/EmptyStandbyList.exe");
            if (utility.exists()) {
                powerShellService.executeSync("& '" + utility.getAbsolutePath() + "' workingsets");
                return true;
            } else {
                powerShellService.executeSync("Get-Process | Where-Object { $_.MainWindowHandle -ne 0 } | ForEach-Object { try { $_.MinWorkingSet = [System.IntPtr]::Zero } catch {} }");
                powerShellService.executeSync("[GC]::Collect()");
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isMemoryCompressionEnabled() {
        try {
            String output = powerShellService.executeSync("Get-MMAgent | Select-Object -ExpandProperty MemoryCompression");
            return "True".equalsIgnoreCase(output.trim());
        } catch (Exception e) {
            return true;
        }
    }
}
