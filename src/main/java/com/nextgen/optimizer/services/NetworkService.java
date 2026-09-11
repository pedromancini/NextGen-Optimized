package com.nextgen.optimizer.services;

import com.sun.jna.platform.win32.WinReg;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Network optimization service: DNS management, Nagle algorithm control,
 * TCP/IP reset operations, adapter diagnostics, and DNS benchmarking.
 */
public class NetworkService {

    private static final WinReg.HKEY HKLM = WinReg.HKEY_LOCAL_MACHINE;
    private static final String TCPIP_INTERFACES_PATH =
            "SYSTEM\\CurrentControlSet\\Services\\Tcpip\\Parameters\\Interfaces";

    private final PowerShellService ps;
    private final RegistryService reg;
    private final BackupService backup;

    public NetworkService(PowerShellService powerShellService,
                          RegistryService registryService,
                          BackupService backupService) {
        this.ps = powerShellService;
        this.reg = registryService;
        this.backup = backupService;
    }

    // ═══════════════════════════════════════════════════════════════
    //  NETWORK RESET COMMANDS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Flush the DNS resolver cache.
     *
     * @return command output
     */
    public String flushDns() {
        return ps.executeSync("ipconfig /flushdns");
    }

    /**
     * Release and renew the DHCP IP address.
     *
     * @return combined output
     */
    public String renewIp() {
        String release = ps.executeSync("ipconfig /release");
        String renew = ps.executeSync("ipconfig /renew");
        return release + "\n" + renew;
    }

    /**
     * Reset the Winsock catalog.
     *
     * @return command output
     */
    public String resetWinsock() {
        return ps.executeSync("netsh winsock reset");
    }

    /**
     * Reset the TCP/IP stack.
     *
     * @return command output
     */
    public String resetTcpIp() {
        return ps.executeSync("netsh int ip reset");
    }

    // ═══════════════════════════════════════════════════════════════
    //  NAGLE ALGORITHM
    // ═══════════════════════════════════════════════════════════════

    /**
     * Disable Nagle's algorithm on the active network adapter for lower latency.
     * Sets TcpNoDelay=1 and TcpAckFrequency=1 in the registry.
     *
     * @return true if successful
     */
    public boolean disableNagle() {
        String adapterGuid = getActiveAdapterGuid();
        if (adapterGuid == null || adapterGuid.isEmpty()) return false;

        String path = TCPIP_INTERFACES_PATH + "\\" + adapterGuid;

        // Backup current values
        BackupService.BackupSnapshot snap = backup.createBackup("nagle-disable");
        backup.addEntry(snap, path, "TcpNoDelay", HKLM);
        backup.addEntry(snap, path, "TcpAckFrequency", HKLM);
        backup.saveBackup(snap);

        boolean ok1 = reg.setIntValue(HKLM, path, "TcpNoDelay", 1);
        boolean ok2 = reg.setIntValue(HKLM, path, "TcpAckFrequency", 1);
        return ok1 && ok2;
    }

    /**
     * Check if Nagle's algorithm is disabled on the active adapter.
     */
    public boolean isNagleDisabled() {
        String adapterGuid = getActiveAdapterGuid();
        if (adapterGuid == null || adapterGuid.isEmpty()) return false;

        String path = TCPIP_INTERFACES_PATH + "\\" + adapterGuid;
        int noDelay = reg.getIntValue(HKLM, path, "TcpNoDelay", 0);
        int ackFreq = reg.getIntValue(HKLM, path, "TcpAckFrequency", 2);
        return noDelay == 1 && ackFreq == 1;
    }

    /**
     * Find the GUID of the active network adapter by looking for one with a
     * DhcpIPAddress or IPAddress set.
     *
     * @return the adapter GUID (e.g. "{XXXXXXXX-...}"), or null
     */
    public String getActiveAdapterGuid() {
        try {
            String[] subKeys = reg.getSubKeys(HKLM, TCPIP_INTERFACES_PATH);
            for (String guid : subKeys) {
                String path = TCPIP_INTERFACES_PATH + "\\" + guid;

                // Check if this adapter has an IP address assigned
                String dhcpIp = reg.getStringValue(HKLM, path, "DhcpIPAddress", "");
                String staticIp = reg.getStringValue(HKLM, path, "IPAddress", "");

                // Filter out 0.0.0.0 and empty
                if (isValidIp(dhcpIp) || isValidIp(staticIp)) {
                    return guid;
                }
            }
        } catch (Exception e) {
            System.err.println("[NetworkService] Error finding active adapter: " + e.getMessage());
        }

        // Fallback: use PowerShell to find the active adapter GUID
        String psOutput = ps.executeSync(
                "(Get-NetAdapter | Where-Object { $_.Status -eq 'Up' } | Select-Object -First 1 " +
                "-ExpandProperty InterfaceGuid)");
        return (psOutput != null && !psOutput.isBlank()) ? psOutput.trim() : null;
    }

    // ═══════════════════════════════════════════════════════════════
    //  MTU
    // ═══════════════════════════════════════════════════════════════

    /**
     * Set the MTU on the active network interface.
     *
     * @param value MTU value (typically 1400–1500)
     * @return command output
     */
    public String setMtu(int value) {
        if (value < 576 || value > 9000) {
            return "MTU inválido. Use um valor entre 576 e 9000.";
        }

        // Get the interface name first
        String ifName = ps.executeSync(
                "(Get-NetAdapter | Where-Object { $_.Status -eq 'Up' } | " +
                "Select-Object -First 1 -ExpandProperty Name)").trim();
        if (ifName.isEmpty()) return "No active adapter found";

        return ps.executeSync(
                "netsh interface ipv4 set subinterface \"" + ifName + "\" mtu=" + value + " store=persistent");
    }

    // ═══════════════════════════════════════════════════════════════
    //  DNS MANAGEMENT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get the currently configured DNS servers on the active adapter.
     *
     * @return array of DNS server IPs, or empty array
     */
    public String[] getCurrentDns() {
        String output = ps.executeSync(
                "(Get-DnsClientServerAddress -AddressFamily IPv4 | " +
                "Where-Object { $_.ServerAddresses.Count -gt 0 } | " +
                "Select-Object -First 1 -ExpandProperty ServerAddresses) -join ','");
        if (output == null || output.isBlank()) return new String[0];
        return Arrays.stream(output.trim().split(","))
                .map(String::trim)
                .filter(this::isValidIpv4)
                .toArray(String[]::new);
    }

    /**
     * Set DNS servers on the active network adapter.
     *
     * @param primary   primary DNS IP
     * @param secondary secondary DNS IP
     * @return true if successful
     */
    public boolean setDns(String primary, String secondary) {
        if (!isValidIpv4(primary) || !isValidIpv4(secondary)) {
            return false;
        }

        try {
            // Apply DNS via PowerShell cmdlet on all active adapters + netsh fallback
            String psScript = "$adapters = Get-NetAdapter | Where-Object { $_.Status -eq 'Up' }; " +
                              "foreach ($a in $adapters) { " +
                              "  try { " +
                              "    Set-DnsClientServerAddress -InterfaceIndex $a.ifIndex -ServerAddresses @('" + primary + "','" + secondary + "') -ErrorAction SilentlyContinue; " +
                              "    netsh interface ipv4 set dnsservers name=`\"$($a.Name)`\" static " + primary + " primary; " +
                              "    netsh interface ipv4 add dnsservers name=`\"$($a.Name)`\" " + secondary + " index=2 " +
                              "  } catch {} " +
                              "}; " +
                              "Clear-DnsClientCache -ErrorAction SilentlyContinue; ipconfig /flushdns";
            PowerShellService.CommandResult result = ps.executeResult(psScript, 45);
            if (!result.isSuccess() && result.output().isBlank()) {
                return false;
            }

            // Verify that DNS was actually applied to at least one adapter
            String check = ps.executeSync("(Get-DnsClientServerAddress -AddressFamily IPv4 | Where-Object { $_.ServerAddresses -contains '" + primary + "' }).Count");
            return check != null && !check.trim().equals("0");
        } catch (Exception e) {
            System.err.println("[NetworkService] Error setting DNS: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get well-known DNS presets.
     *
     * @return Map of name → {primary, secondary}
     */
    public Map<String, String[]> getDnsPresets() {
        Map<String, String[]> presets = new LinkedHashMap<>();
        presets.put("Cloudflare", new String[]{"1.1.1.1", "1.0.0.1"});
        presets.put("Google", new String[]{"8.8.8.8", "8.8.4.4"});
        presets.put("Quad9", new String[]{"9.9.9.9", "149.112.112.112"});
        presets.put("OpenDNS", new String[]{"208.67.222.222", "208.67.220.220"});
        presets.put("AdGuard", new String[]{"94.140.14.14", "94.140.15.15"});
        presets.put("CleanBrowsing", new String[]{"185.228.168.9", "185.228.169.9"});
        return presets;
    }

    /**
     * Benchmark all DNS presets by measuring latency (ICMP ping) to each primary IP.
     *
     * @return Map of preset name → latency in milliseconds (-1 if unreachable)
     */
    public Map<String, Long> benchmarkDns() {
        Map<String, Long> results = new LinkedHashMap<>();
        Map<String, String[]> presets = getDnsPresets();

        for (Map.Entry<String, String[]> entry : presets.entrySet()) {
            String ip = entry.getValue()[0];
            long latency = pingHost(ip);
            results.put(entry.getKey(), latency);
        }
        return results;
    }

    /**
     * Get the current ping latency to 8.8.8.8.
     *
     * @return latency in milliseconds, or -1 if unreachable
     */
    public double getCurrentPing() {
        return pingHost("8.8.8.8");
    }

    // ─── Helpers ─────────────────────────────────────────────────

    /**
     * Ping a host and return the average latency in ms.
     */
    private long pingHost(String host) {
        try {
            String output = ps.executeSync("ping -n 3 -w 1000 " + host);
            if (output == null) return -1;

            // Parse "Average = XXms" from ping output
            // English: "Average = 12ms"  |  Portuguese: "M\u00e9dia = 12ms"
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?:Average|M.dia|Moyenne|Promedio|Durchschnitt)\\s*=\\s*(\\d+)\\s*ms",
                            java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(output);
            if (m.find()) {
                return Long.parseLong(m.group(1));
            }

            // Fallback: look for any "time=XXms" and average them
            java.util.regex.Matcher timeMatcher = java.util.regex.Pattern
                    .compile("(?:time|tempo|temps|zeit|tiempo)[=<]\\s*(\\d+)\\s*ms",
                            java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(output);
            long sum = 0;
            int count = 0;
            while (timeMatcher.find()) {
                sum += Long.parseLong(timeMatcher.group(1));
                count++;
            }
            return count > 0 ? sum / count : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean isValidIp(String ip) {
        return ip != null && !ip.isEmpty() && !ip.equals("0.0.0.0");
    }

    private boolean isValidIpv4(String ip) {
        if (ip == null || ip.isBlank()) return false;
        String[] parts = ip.trim().split("\\.");
        if (parts.length != 4) return false;
        for (String part : parts) {
            try {
                if (part.isEmpty() || (part.length() > 1 && part.startsWith("0"))) return false;
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }
}
