package com.nextgen.optimizer.services;

import com.sun.jna.platform.win32.WinReg;

/**
 * Service for CPU management, Core Parking, and CPU Boost settings.
 */
public class CpuService {

    private final PowerShellService powerShellService;
    private final RegistryService registryService;
    private final BackupService backupService;
    private final SystemInfoService systemInfoService;

    public CpuService(PowerShellService powerShellService, RegistryService registryService,
                      BackupService backupService, SystemInfoService systemInfoService) {
        this.powerShellService = powerShellService;
        this.registryService = registryService;
        this.backupService = backupService;
        this.systemInfoService = systemInfoService;
    }

    public String getCpuModel() {
        try {
            return powerShellService.executeSync("Get-WmiObject Win32_Processor | Select-Object -ExpandProperty Name").trim();
        } catch (Exception e) {
            return "Processador Multicore";
        }
    }

    public int getCoreCount() {
        return Runtime.getRuntime().availableProcessors() / 2;
    }

    public int getThreadCount() {
        return Runtime.getRuntime().availableProcessors();
    }

    public boolean isCoreParkedSettingExposed() {
        int attr = registryService.getIntValue(WinReg.HKEY_LOCAL_MACHINE,
            "SYSTEM\\CurrentControlSet\\Control\\Power\\PowerSettings\\54533251-82be-4824-96c1-47b60b740d00\\0cc5b647-c1df-4637-891a-dec35c318583",
            "Attributes");
        return attr == 0;
    }

    public void setCoreParking(boolean enabled) {
        try {
            registryService.setIntValue(WinReg.HKEY_LOCAL_MACHINE,
                "SYSTEM\\CurrentControlSet\\Control\\Power\\PowerSettings\\54533251-82be-4824-96c1-47b60b740d00\\0cc5b647-c1df-4637-891a-dec35c318583",
                "Attributes", enabled ? 1 : 0);
            powerShellService.executeSync("powercfg /setacvalueindex SCHEME_CURRENT SUB_PROCESSOR CPMINCORES " + (enabled ? "10" : "100"));
            powerShellService.executeSync("powercfg /setactive SCHEME_CURRENT");
        } catch (Exception ignored) {}
    }

    public boolean isCpuBoostEnabled() {
        try {
            String output = powerShellService.executeSync("powercfg /q SCHEME_CURRENT SUB_PROCESSOR PERFBOOSTMODE");
            return !output.contains("0x00000000");
        } catch (Exception e) {
            return true;
        }
    }

    public void setCpuBoost(boolean enabled) {
        try {
            powerShellService.executeSync("powercfg /setacvalueindex SCHEME_CURRENT SUB_PROCESSOR PERFBOOSTMODE " + (enabled ? "2" : "0"));
            powerShellService.executeSync("powercfg /setactive SCHEME_CURRENT");
        } catch (Exception ignored) {}
    }

    public boolean isHyperThreadingEnabled() {
        return getThreadCount() > getCoreCount();
    }
}
