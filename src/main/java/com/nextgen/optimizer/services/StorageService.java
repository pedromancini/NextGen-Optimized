package com.nextgen.optimizer.services;

import com.sun.nio.file.ExtendedOpenOption;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Drives, disk health, TRIM/defrag and an honest sequential speed test.
 * Every value shown comes from Windows; nothing is estimated or invented.
 */
public class StorageService {

    /** Physical disk details for one drive letter. */
    public record DiskInfo(String mediaType, String health, String busType, String model) {
        public boolean isSsd() {
            return "SSD".equalsIgnoreCase(mediaType) || "NVMe".equalsIgnoreCase(busType);
        }
    }

    private final PowerShellService powerShellService;
    private final SystemInfoService systemInfoService;

    public StorageService(PowerShellService powerShellService, SystemInfoService systemInfoService) {
        this.powerShellService = powerShellService;
        this.systemInfoService = systemInfoService;
    }

    /** One query for all volumes: letter → physical disk behind it. */
    public Map<String, DiskInfo> diskInfoByLetter() {
        Map<String, DiskInfo> map = new HashMap<>();
        String out = powerShellService.executeResult(
                "Get-Partition | Where-Object DriveLetter | ForEach-Object { $p = $_; "
                        + "$d = Get-PhysicalDisk | Where-Object DeviceId -eq ([string]$p.DiskNumber) | Select-Object -First 1; "
                        + "'{0}|{1}|{2}|{3}|{4}' -f $p.DriveLetter, $d.MediaType, $d.HealthStatus, $d.BusType, $d.FriendlyName }",
                60).output();
        for (String line : out.split("\\R")) {
            String[] parts = line.split("\\|", -1);
            if (parts.length >= 5 && parts[0].trim().length() == 1) {
                map.put(parts[0].trim().toUpperCase(Locale.ROOT),
                        new DiskInfo(parts[1].trim(), parts[2].trim(), parts[3].trim(), parts[4].trim()));
            }
        }
        return map;
    }

    public List<Map<String, Object>> getDrives() {
        Map<String, DiskInfo> info = diskInfoByLetter();
        List<Map<String, Object>> drives = new ArrayList<>();
        File[] roots = File.listRoots();
        if (roots == null) return drives;
        for (File r : roots) {
            if (r.getTotalSpace() <= 0) continue;
            String letter = r.getAbsolutePath().substring(0, 1).toUpperCase(Locale.ROOT);
            DiskInfo d = info.getOrDefault(letter, new DiskInfo("", "", "", ""));
            Map<String, Object> map = new HashMap<>();
            map.put("letter", r.getAbsolutePath());
            map.put("total", r.getTotalSpace());
            map.put("free", r.getFreeSpace());
            map.put("used", r.getTotalSpace() - r.getFreeSpace());
            map.put("type", d.isSsd() ? "SSD" : d.mediaType().isBlank() ? "Disco" : "HD");
            map.put("info", d);
            drives.add(map);
        }
        return drives;
    }

    public boolean runTrim(String driveLetter) {
        return powerShellService.executeResult("Optimize-Volume -DriveLetter " + letter(driveLetter) + " -ReTrim -ErrorAction Stop", 600).isSuccess();
    }

    public boolean runDefrag(String driveLetter) {
        return powerShellService.executeResult("Optimize-Volume -DriveLetter " + letter(driveLetter) + " -Defrag -ErrorAction Stop", 3600).isSuccess();
    }

    public boolean runChkdsk(String driveLetter) {
        // /scan is online and read-only: it never locks or repairs the volume.
        return powerShellService.runProcess(1800, "chkdsk.exe", letter(driveLetter) + ":", "/scan").exitCode() == 0;
    }

    /**
     * Sequential read/write with unbuffered (direct) I/O so the Windows cache
     * does not inflate the result.
     *
     * @return {readMBps, writeMBps}, or {@code null} if the test could not run.
     */
    public double[] runBenchmark(String driveLetter) {
        final int chunk = 8 * 1024 * 1024;
        final int chunks = 32; // 256 MB
        Path dir = letter(driveLetter).equalsIgnoreCase(System.getenv().getOrDefault("SystemDrive", "C:").substring(0, 1))
                ? Path.of(System.getProperty("java.io.tmpdir"))
                : Path.of(letter(driveLetter) + ":\\");
        Path file = dir.resolve("nextgenx_bench_" + System.nanoTime() + ".tmp");
        try {
            int block = (int) Math.max(4096, Files.getFileStore(dir).getBlockSize());
            ByteBuffer buffer = ByteBuffer.allocateDirect(chunk + block).alignedSlice(block).slice(0, chunk);
            byte[] noise = new byte[chunk];
            new Random(42).nextBytes(noise);
            buffer.put(noise);

            long start = System.nanoTime();
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                    ExtendedOpenOption.DIRECT)) {
                for (int i = 0; i < chunks; i++) {
                    buffer.clear();
                    while (buffer.hasRemaining()) ch.write(buffer);
                }
                ch.force(true);
            }
            double write = chunks * (chunk / 1048576.0) / ((System.nanoTime() - start) / 1e9);

            start = System.nanoTime();
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ, ExtendedOpenOption.DIRECT)) {
                for (int i = 0; i < chunks; i++) {
                    buffer.clear();
                    while (buffer.hasRemaining() && ch.read(buffer) > 0) { /* keep reading */ }
                }
            }
            double read = chunks * (chunk / 1048576.0) / ((System.nanoTime() - start) / 1e9);
            return new double[]{Math.round(read), Math.round(write)};
        } catch (Exception e) {
            System.err.println("[StorageService] Benchmark failed: " + e.getMessage());
            return null;
        } finally {
            try {
                Files.deleteIfExists(file);
            } catch (Exception ignored) {}
        }
    }

    public String getDetailedSmartInfo(String driveLetter) {
        String out = powerShellService.executeResult(
                "$p = Get-Partition -DriveLetter " + letter(driveLetter) + " -ErrorAction Stop; "
                        + "$d = Get-PhysicalDisk | Where-Object DeviceId -eq ([string]$p.DiskNumber) | Select-Object -First 1; "
                        + "$r = $d | Get-StorageReliabilityCounter -ErrorAction SilentlyContinue; "
                        + "'{0} · {1} · saúde: {2}' -f $d.FriendlyName, $d.MediaType, $d.HealthStatus; "
                        + "if ($r) { 'Temperatura: {0} °C · Desgaste: {1}% · Horas ligado: {2} · Erros de leitura: {3}' -f "
                        + "$r.Temperature, $r.Wear, $r.PowerOnHours, $r.ReadErrorsTotal }",
                60).output().trim();
        return out.isBlank() ? "O Windows não informou dados S.M.A.R.T. para esta unidade." : out.replace("\r\n", "\n");
    }

    private static String letter(String driveLetter) {
        return driveLetter == null || driveLetter.isBlank() ? "C" : driveLetter.substring(0, 1).toUpperCase(Locale.ROOT);
    }
}
