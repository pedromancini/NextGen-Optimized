package com.nextgen.optimizer.services;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for GPU detection and monitoring via nvidia-smi / WMI.
 */
public class GpuService {

    private final PowerShellService powerShellService;
    private final SystemInfoService systemInfoService;

    public GpuService(PowerShellService powerShellService, SystemInfoService systemInfoService) {
        this.powerShellService = powerShellService;
        this.systemInfoService = systemInfoService;
    }

    public String detectGpu() {
        try {
            String output = powerShellService.executeSync("Get-WmiObject Win32_VideoController | Select-Object -ExpandProperty Name");
            if (output.toLowerCase().contains("nvidia")) return "NVIDIA";
            if (output.toLowerCase().contains("amd") || output.toLowerCase().contains("radeon")) return "AMD";
            if (output.toLowerCase().contains("intel")) return "Intel";
            return "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }

    public String getGpuName() {
        try {
            String output = powerShellService.executeSync("Get-WmiObject Win32_VideoController | Select-Object -ExpandProperty Name");
            String[] lines = output.split("\\r?\\n");
            for (String l : lines) {
                if (!l.trim().isEmpty()) return l.trim();
            }
            return "GPU Genérica";
        } catch (Exception e) {
            return "Placa de Vídeo";
        }
    }

    public String getGpuDriver() {
        try {
            return powerShellService.executeSync("Get-WmiObject Win32_VideoController | Select-Object -ExpandProperty DriverVersion").trim();
        } catch (Exception e) {
            return "Desconhecido";
        }
    }

    public long getGpuVramTotal() {
        try {
            String output = powerShellService.executeSync("Get-WmiObject Win32_VideoController | Select-Object -ExpandProperty AdapterRAM");
            return Long.parseLong(output.trim()) / (1024 * 1024);
        } catch (Exception e) {
            return 4096;
        }
    }

    public Map<String, String> getNvidiaInfo() {
        Map<String, String> info = new HashMap<>();
        try {
            String output = powerShellService.executeSync("nvidia-smi --query-gpu=temperature.gpu,utilization.gpu,clocks.gr,clocks.mem,memory.used,memory.total,fan.speed,power.draw --format=csv,noheader,nounits");
            String[] parts = output.trim().split(",\\s*");
            if (parts.length >= 8) {
                info.put("temp", parts[0]);
                info.put("utilization", parts[1]);
                info.put("clockCore", parts[2]);
                info.put("clockMem", parts[3]);
                info.put("vramUsed", parts[4]);
                info.put("vramTotal", parts[5]);
                info.put("fan", parts[6]);
                info.put("power", parts[7]);
            }
        } catch (Exception ignored) {}
        return info;
    }

    public boolean isNvidiaAvailable() {
        return "NVIDIA".equalsIgnoreCase(detectGpu());
    }

    public NvidiaProfileResult createNvidiaLowLatencyProfile() {
        try {
            Path profilePath = getNvidiaProfilePath();
            Files.createDirectories(profilePath.getParent());
            Files.writeString(profilePath, buildLowLatencyNipXml(), StandardCharsets.UTF_16);
            return new NvidiaProfileResult(true, "Perfil .nip gerado com sucesso.", profilePath.toString(), findNvidiaProfileInspector());
        } catch (Exception e) {
            return new NvidiaProfileResult(false, "Falha ao gerar perfil NVIDIA: " + e.getMessage(), "", "");
        }
    }

    public NvidiaProfileResult applyNvidiaLowLatencyProfile() {
        if (!isNvidiaAvailable()) {
            return new NvidiaProfileResult(false, "Nenhuma GPU NVIDIA foi detectada.", "", "");
        }

        NvidiaProfileResult generated = createNvidiaLowLatencyProfile();
        if (!generated.success()) {
            return generated;
        }

        String inspector = generated.toolPath();
        if (inspector == null || inspector.isBlank()) {
            return new NvidiaProfileResult(false,
                    "Perfil gerado, mas nvidiaProfileInspector.exe nao foi encontrado. Importe manualmente o .nip.",
                    generated.profilePath(),
                    "");
        }

        try {
            String escapedExe = inspector.replace("'", "''");
            String escapedProfile = generated.profilePath().replace("'", "''");
            String command = "Start-Process -FilePath '" + escapedExe + "' " +
                    "-ArgumentList @('-silentImport','" + escapedProfile + "') " +
                    "-Verb RunAs -Wait -WindowStyle Hidden";
            powerShellService.executeSync(command);
            return new NvidiaProfileResult(true,
                    "Perfil NVIDIA enviado para o Profile Inspector. Confirme o UAC se aparecer.",
                    generated.profilePath(),
                    inspector);
        } catch (Exception e) {
            return new NvidiaProfileResult(false,
                    "Perfil gerado, mas nao foi possivel iniciar o Profile Inspector: " + e.getMessage(),
                    generated.profilePath(),
                    inspector);
        }
    }

    public String findNvidiaProfileInspector() {
        List<Path> candidates = List.of(
                Path.of("tools", "nvidiaProfileInspector.exe"),
                Path.of("nvidia", "nvidiaProfileInspector.exe"),
                Path.of("nvidiaProfileInspector.exe"),
                Path.of(System.getProperty("user.home"), "Downloads", "nvidiaProfileInspector.exe"),
                Path.of(System.getProperty("user.home"), "Desktop", "nvidiaProfileInspector.exe"),
                Path.of(System.getProperty("user.home"), "OneDrive", "Área de Trabalho", "nvidiaProfileInspector.exe"),
                Path.of("C:\\Program Files\\nvidiaProfileInspector\\nvidiaProfileInspector.exe"),
                Path.of("C:\\Program Files (x86)\\nvidiaProfileInspector\\nvidiaProfileInspector.exe")
        );

        for (Path candidate : candidates) {
            try {
                if (Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath().normalize().toString();
                }
            } catch (Exception ignored) {}
        }

        String fromPath = powerShellService.executeSync(
                "(Get-Command nvidiaProfileInspector.exe -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty Source)"
        );
        return fromPath == null ? "" : fromPath.trim();
    }

    public Path getNvidiaProfilePath() {
        return Path.of(System.getProperty("user.home"), ".nextgen", "nvidia", "NextGen_NVIDIA_LowLatency_BaseProfile.nip");
    }

    private String buildLowLatencyNipXml() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"utf-16\"?>\n");
        xml.append("<ArrayOfProfile>\n");
        xml.append("  <Profile>\n");
        xml.append("    <ProfileName>Base Profile</ProfileName>\n");
        xml.append("    <Executeables />\n");
        xml.append("    <Settings>\n");

        addSetting(xml, "Frame Rate Limiter V3", 0x10835002L, 0);
        addSetting(xml, "Frame Rate Limiter - Background Application", 0x10835005L, 0);
        addSetting(xml, "Preferred refresh rate", 6600001L, 1);
        addSetting(xml, "Maximum pre-rendered frames", 8102046L, 1);
        addSetting(xml, "Ultra Low Latency - CPL State", 0x0005F543L, 0);
        addSetting(xml, "Ultra Low Latency", 0x10835000L, 0);
        addSetting(xml, "Vertical Sync", 11041231L, 0x08416747L);
        addSetting(xml, "Flag to control smooth AFR behavior", 270198627L, 0);
        addSetting(xml, "Vertical Sync Tear Control", 5912412L, 0x96861077L);
        addSetting(xml, "Triple buffering", 553505273L, 0);
        addSetting(xml, "GSYNC - Indicator Overlay", 0x10029538L, 0);
        addSetting(xml, "GSYNC - Application Mode", 0x1194F158L, 0);
        addSetting(xml, "GSYNC - Application Requested State", 0x10A879ACL, 1);
        addSetting(xml, "GSYNC - Application State", 0x10A879CFL, 1);
        addSetting(xml, "GSYNC - Global Feature", 0x1094F157L, 0);
        addSetting(xml, "GSYNC - Support Indicator Overlay", 0x008DF510L, 0);
        addSetting(xml, "Antialiasing - FXAA Enabled", 276089202L, 0);
        addSetting(xml, "Antialiasing - FXAA Enabled predefined by NVIDIA", 0x1034CB89L, 0);
        addSetting(xml, "Antialiasing - Gamma correction", 276652957L, 0);
        addSetting(xml, "Antialiasing - Mode", 276757595L, 0);
        addSetting(xml, "Antialiasing - Setting", 282555346L, 0);
        addSetting(xml, "Antialiasing - Behavior Flags", 283958146L, 0);
        addSetting(xml, "Antialiasing - MFAA Enabled", 0x101AE815L, 0);
        addSetting(xml, "Antialiasing - Transparency Multisampling", 0x200AC877L, 0);
        addSetting(xml, "Antialiasing - Transparency Supersampling", 282364549L, 0);
        addSetting(xml, "Sharpening - Ignore Film Grain", 0x00598927L, 0);
        addSetting(xml, "Sharpening Value", 0x002ED8CDL, 0);
        addSetting(xml, "Sharpening Filter", 0x00598928L, 0);

        xml.append("    </Settings>\n");
        xml.append("  </Profile>\n");
        xml.append("</ArrayOfProfile>\n");
        return xml.toString();
    }

    private void addSetting(StringBuilder xml, String name, long id, long value) {
        xml.append("      <ProfileSetting>\n");
        xml.append("        <SettingNameInfo>").append(escapeXml(name)).append("</SettingNameInfo>\n");
        xml.append("        <SettingID>").append(Long.toUnsignedString(id)).append("</SettingID>\n");
        xml.append("        <SettingValue>").append(Long.toUnsignedString(value)).append("</SettingValue>\n");
        xml.append("        <ValueType>Dword</ValueType>\n");
        xml.append("      </ProfileSetting>\n");
    }

    private String escapeXml(String text) {
        return text == null ? "" : text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public record NvidiaProfileResult(boolean success, String message, String profilePath, String toolPath) {}
}
