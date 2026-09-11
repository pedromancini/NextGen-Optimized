package com.nextgen.optimizer.model;

/**
 * Data-transfer object that captures all system metrics at a single point in time.
 * Instances are created by {@link com.nextgen.optimizer.services.SystemInfoService}
 * on every monitoring tick and exposed as a JavaFX property for UI binding.
 */
public class SystemSnapshot {

    // ── CPU ──────────────────────────────────────────────────────────────
    private double cpuUsage;           // 0-100 %
    private double[] cpuCoreLoads;     // per-core 0-100 %
    private double cpuTemperature;     // °C
    private long cpuFrequency;         // MHz
    private String cpuModel = "";
    private int cpuCores;
    private int cpuThreads;

    // ── GPU ──────────────────────────────────────────────────────────────
    private double gpuUsage;           // 0-100 %
    private double gpuTemperature;     // °C
    private long gpuCoreClock;         // MHz
    private long gpuMemClock;          // MHz
    private long gpuVramUsed;          // MB
    private long gpuVramTotal;         // MB
    private String gpuName = "";
    private String gpuVendor = "";
    private double gpuFanSpeed;        // 0-100 %
    private double gpuPower;           // Watts

    // ── RAM ──────────────────────────────────────────────────────────────
    private long ramTotal;             // bytes
    private long ramUsed;              // bytes
    private long ramAvailable;         // bytes
    private double ramUsagePercent;    // 0-100 %

    // ── Network ─────────────────────────────────────────────────────────
    private double networkPing;        // ms
    private double networkDownload;    // Mbps
    private double networkUpload;      // Mbps

    // ── Disk ────────────────────────────────────────────────────────────
    private double diskReadSpeed;      // MB/s
    private double diskWriteSpeed;     // MB/s
    private double diskUsagePercent;   // 0-100 %

    // ── Timestamp ───────────────────────────────────────────────────────
    private long timestamp;            // epoch millis

    /** No-arg constructor. */
    public SystemSnapshot() {
        this.timestamp = System.currentTimeMillis();
        this.cpuCoreLoads = new double[0];
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CPU getters / setters
    // ═══════════════════════════════════════════════════════════════════

    public double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public double[] getCpuCoreLoads() {
        return cpuCoreLoads;
    }

    public void setCpuCoreLoads(double[] cpuCoreLoads) {
        this.cpuCoreLoads = cpuCoreLoads;
    }

    public double getCpuTemperature() {
        return cpuTemperature;
    }

    public void setCpuTemperature(double cpuTemperature) {
        this.cpuTemperature = cpuTemperature;
    }

    public long getCpuFrequency() {
        return cpuFrequency;
    }

    public void setCpuFrequency(long cpuFrequency) {
        this.cpuFrequency = cpuFrequency;
    }

    public String getCpuModel() {
        return cpuModel;
    }

    public void setCpuModel(String cpuModel) {
        this.cpuModel = cpuModel;
    }

    public int getCpuCores() {
        return cpuCores;
    }

    public void setCpuCores(int cpuCores) {
        this.cpuCores = cpuCores;
    }

    public int getCpuThreads() {
        return cpuThreads;
    }

    public void setCpuThreads(int cpuThreads) {
        this.cpuThreads = cpuThreads;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  GPU getters / setters
    // ═══════════════════════════════════════════════════════════════════

    public double getGpuUsage() {
        return gpuUsage;
    }

    public void setGpuUsage(double gpuUsage) {
        this.gpuUsage = gpuUsage;
    }

    public double getGpuTemperature() {
        return gpuTemperature;
    }

    public void setGpuTemperature(double gpuTemperature) {
        this.gpuTemperature = gpuTemperature;
    }

    public long getGpuCoreClock() {
        return gpuCoreClock;
    }

    public void setGpuCoreClock(long gpuCoreClock) {
        this.gpuCoreClock = gpuCoreClock;
    }

    public long getGpuMemClock() {
        return gpuMemClock;
    }

    public void setGpuMemClock(long gpuMemClock) {
        this.gpuMemClock = gpuMemClock;
    }

    public long getGpuVramUsed() {
        return gpuVramUsed;
    }

    public void setGpuVramUsed(long gpuVramUsed) {
        this.gpuVramUsed = gpuVramUsed;
    }

    public long getGpuVramTotal() {
        return gpuVramTotal;
    }

    public void setGpuVramTotal(long gpuVramTotal) {
        this.gpuVramTotal = gpuVramTotal;
    }

    public String getGpuName() {
        return gpuName;
    }

    public void setGpuName(String gpuName) {
        this.gpuName = gpuName;
    }

    public String getGpuVendor() {
        return gpuVendor;
    }

    public void setGpuVendor(String gpuVendor) {
        this.gpuVendor = gpuVendor;
    }

    public double getGpuFanSpeed() {
        return gpuFanSpeed;
    }

    public void setGpuFanSpeed(double gpuFanSpeed) {
        this.gpuFanSpeed = gpuFanSpeed;
    }

    public double getGpuPower() {
        return gpuPower;
    }

    public void setGpuPower(double gpuPower) {
        this.gpuPower = gpuPower;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  RAM getters / setters
    // ═══════════════════════════════════════════════════════════════════

    public long getRamTotal() {
        return ramTotal;
    }

    public void setRamTotal(long ramTotal) {
        this.ramTotal = ramTotal;
    }

    public long getRamUsed() {
        return ramUsed;
    }

    public void setRamUsed(long ramUsed) {
        this.ramUsed = ramUsed;
    }

    public long getRamAvailable() {
        return ramAvailable;
    }

    public void setRamAvailable(long ramAvailable) {
        this.ramAvailable = ramAvailable;
    }

    public double getRamUsagePercent() {
        return ramUsagePercent;
    }

    public void setRamUsagePercent(double ramUsagePercent) {
        this.ramUsagePercent = ramUsagePercent;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Network getters / setters
    // ═══════════════════════════════════════════════════════════════════

    public double getNetworkPing() {
        return networkPing;
    }

    public void setNetworkPing(double networkPing) {
        this.networkPing = networkPing;
    }

    public double getNetworkDownload() {
        return networkDownload;
    }

    public void setNetworkDownload(double networkDownload) {
        this.networkDownload = networkDownload;
    }

    public double getNetworkUpload() {
        return networkUpload;
    }

    public void setNetworkUpload(double networkUpload) {
        this.networkUpload = networkUpload;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Disk getters / setters
    // ═══════════════════════════════════════════════════════════════════

    public double getDiskReadSpeed() {
        return diskReadSpeed;
    }

    public void setDiskReadSpeed(double diskReadSpeed) {
        this.diskReadSpeed = diskReadSpeed;
    }

    public double getDiskWriteSpeed() {
        return diskWriteSpeed;
    }

    public void setDiskWriteSpeed(double diskWriteSpeed) {
        this.diskWriteSpeed = diskWriteSpeed;
    }

    public double getDiskUsagePercent() {
        return diskUsagePercent;
    }

    public void setDiskUsagePercent(double diskUsagePercent) {
        this.diskUsagePercent = diskUsagePercent;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Timestamp
    // ═══════════════════════════════════════════════════════════════════

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "SystemSnapshot{" +
                "cpu=" + String.format("%.1f%%", cpuUsage) +
                ", ram=" + String.format("%.1f%%", ramUsagePercent) +
                ", gpu=" + String.format("%.1f%%", gpuUsage) +
                ", ping=" + String.format("%.0fms", networkPing) +
                ", ts=" + timestamp +
                '}';
    }
}
