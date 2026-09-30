package com.nextgen.optimizer.services;

/**
 * Creates Windows System Restore points — the last-resort safety net that
 * covers everything, including changes made outside NextGen X.
 */
public class SafetyService {

    public record Result(boolean success, String message) {}

    private final PowerShellService shell;

    public SafetyService(PowerShellService shell) {
        this.shell = shell;
    }

    /**
     * Creates a restore point. Windows normally allows one every 24 h; the
     * frequency limit is lifted only for this call and restored afterwards.
     * System Protection is enabled on the system drive if it was off.
     */
    public Result createRestorePoint(String description) {
        String safeDescription = description.replace("'", "''");
        String script = String.join(" ",
                "$ErrorActionPreference = 'Stop';",
                "$key = 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\SystemRestore';",
                "$old = (Get-ItemProperty -Path $key -Name SystemRestorePointCreationFrequency -ErrorAction SilentlyContinue).SystemRestorePointCreationFrequency;",
                "try {",
                "  Set-ItemProperty -Path $key -Name SystemRestorePointCreationFrequency -Value 0 -Type DWord;",
                "  Enable-ComputerRestore -Drive \"$env:SystemDrive\\\" -ErrorAction SilentlyContinue;",
                "  Checkpoint-Computer -Description '" + safeDescription + "' -RestorePointType MODIFY_SETTINGS;",
                "  'NEXTGEN_OK'",
                "} catch { 'NEXTGEN_ERR ' + $_.Exception.Message }",
                "finally {",
                "  if ($null -eq $old) { Remove-ItemProperty -Path $key -Name SystemRestorePointCreationFrequency -ErrorAction SilentlyContinue }",
                "  else { Set-ItemProperty -Path $key -Name SystemRestorePointCreationFrequency -Value $old -Type DWord }",
                "}");
        PowerShellService.CommandResult result = shell.executeResult(script, 300);
        String output = result.output();
        if (output.contains("NEXTGEN_OK")) {
            return new Result(true, "Ponto de restauração criado.");
        }
        if (result.timedOut()) {
            return new Result(false, "O Windows demorou demais para criar o ponto de restauração.");
        }
        int idx = output.indexOf("NEXTGEN_ERR");
        String reason = idx >= 0 ? output.substring(idx + 11).trim() : "sem resposta do Windows";
        return new Result(false, "Não foi possível criar o ponto de restauração: " + reason);
    }

    public void openSystemRestore() {
        shell.runProcess(5, "rstrui.exe");
    }
}
