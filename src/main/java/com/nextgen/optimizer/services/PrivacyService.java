package com.nextgen.optimizer.services;

import com.sun.jna.platform.win32.WinReg;
import java.util.List;

/** Privacy preferences are opt-in actions; opening the page only reads state. */
public class PrivacyService {
    public record Entry(String path, String name, int value) {}
    public record Option(String id, String title, String description, String uri, List<Entry> entries) {}
    public enum State { CONFIGURED, NOT_CONFIGURED, UNKNOWN }
    public enum RecallState { ENABLED, DISABLED, ABSENT, PENDING, UNKNOWN }
    public record Result(boolean success, String message) {}
    private final RegistryService registry;
    private final BackupService backups;
    private final PowerShellService shell;

    public PrivacyService(RegistryService registry, BackupService backups, PowerShellService shell) {
        this.registry = registry;
        this.backups = backups;
        this.shell = shell;
    }

    public List<Option> options() {
        return List.of(
            new Option("typing", "Coleta de digitação e escrita",
                "Desativa o aprendizado de palavras e a contribuição de amostras de digitação. As sugestões personalizadas podem ficar menos precisas. Não é um detector de keyloggers.",
                "ms-settings:privacy-speechtyping", List.of(
                    new Entry("Software\\Microsoft\\InputPersonalization", "RestrictImplicitTextCollection", 1),
                    new Entry("Software\\Microsoft\\InputPersonalization", "RestrictImplicitInkCollection", 1),
                    new Entry("Software\\Microsoft\\InputPersonalization\\TrainedDataStore", "HarvestContacts", 0),
                    new Entry("Software\\Microsoft\\Input\\TIPC", "Enabled", 0))),
            new Option("advertising", "Identificador de publicidade",
                "Impede o uso do identificador de publicidade deste usuário pelos aplicativos. Anúncios ainda podem aparecer.",
                "ms-settings:privacy-general", List.of(
                    new Entry("Software\\Microsoft\\Windows\\CurrentVersion\\AdvertisingInfo", "Enabled", 0))),
            new Option("offers", "Ofertas baseadas em diagnósticos",
                "Desativa experiências personalizadas baseadas em dados de diagnóstico. Isso não desativa os diagnósticos obrigatórios do Windows.",
                "ms-settings:privacy-feedback", List.of(
                    new Entry("Software\\Microsoft\\Windows\\CurrentVersion\\Privacy", "TailoredExperiencesWithDiagnosticDataEnabled", 0)))
        );
    }

    public State state(Option option) {
        boolean unknown = false;
        for (Entry entry : option.entries()) {
            int value = registry.getIntValue(WinReg.HKEY_CURRENT_USER, entry.path(), entry.name(), -1);
            if (value == -1) unknown = true;
            else if (value != entry.value()) return State.NOT_CONFIGURED;
        }
        return unknown ? State.UNKNOWN : State.CONFIGURED;
    }

    public synchronized Result apply(Option option) {
        if (state(option) == State.CONFIGURED) return new Result(true, "Preferências já configuradas.");
        String label = "privacy-v1-" + option.id();
        if (backups.loadLatestBackup(label) == null) {
            var snapshot = backups.createBackup(label);
            for (Entry entry : option.entries())
                backups.addEntry(snapshot, entry.path(), entry.name(), WinReg.HKEY_CURRENT_USER);
            if (!backups.saveBackup(snapshot))
                return new Result(false, "Não foi possível salvar o backup. Nenhuma alteração aplicada.");
        }
        boolean ok = true;
        for (Entry entry : option.entries())
            ok &= registry.setIntValue(WinReg.HKEY_CURRENT_USER, entry.path(), entry.name(), entry.value());
        return new Result(ok && state(option) == State.CONFIGURED,
            ok ? "Preferências gravadas e verificadas. Confira também nas Configurações do Windows."
               : "Algumas alterações falharam. O backup está disponível para restaurar.");
    }

    public boolean hasBackup(Option option) {
        return backups.loadLatestBackup("privacy-v1-" + option.id()) != null;
    }

    public synchronized Result restore(Option option) {
        var snapshot = backups.loadLatestBackup("privacy-v1-" + option.id());
        if (snapshot == null) return new Result(false, "Nenhum backup encontrado para esta opção.");
        boolean ok = backups.restoreBackup(snapshot);
        return new Result(ok, ok ? "Preferências anteriores restauradas." : "Restauração incompleta. Tente novamente como administrador.");
    }

    public RecallState recallState() {
        var result = shell.executeResult("$ErrorActionPreference='Stop'; try { "
            + "$f = Get-WindowsOptionalFeature -Online | Where-Object FeatureName -eq 'Recall'; "
            + "if ($null -eq $f) { 'Absent' } else { $f.State.ToString() } } catch { exit 1 }", 120);
        return result.isSuccess() ? parseRecallState(result.output()) : RecallState.UNKNOWN;
    }

    public static RecallState parseRecallState(String output) {
        return switch (output.trim()) {
            case "Enabled" -> RecallState.ENABLED;
            case "Disabled", "DisabledWithPayloadRemoved" -> RecallState.DISABLED;
            case "Absent" -> RecallState.ABSENT;
            case "DisablePending", "EnablePending" -> RecallState.PENDING;
            default -> RecallState.UNKNOWN;
        };
    }

    public synchronized Result disableRecall() {
        RecallState before = recallState();
        if (before == RecallState.DISABLED || before == RecallState.ABSENT)
            return new Result(true, "Recall já está desativado ou não está instalado.");
        if (before != RecallState.ENABLED)
            return new Result(false, "Não foi possível confirmar o estado do Recall. Verifique permissões ou reinicialização pendente.");
        var result = shell.executeResult("$ErrorActionPreference='Stop'; try { "
            + "Disable-WindowsOptionalFeature -Online -FeatureName 'Recall' -NoRestart -ErrorAction Stop | Out-Null "
            + "} catch { Write-Output $_.Exception.Message; exit 1 }", 600);
        if (!result.isSuccess()) return new Result(false,
            "O Windows não confirmou a conclusão. Atualize o estado antes de tentar novamente.");
        RecallState after = recallState();
        return new Result(after == RecallState.DISABLED || after == RecallState.PENDING,
            after == RecallState.PENDING ? "Recall: reinicie o Windows para concluir."
            : after == RecallState.DISABLED ? "Componente Recall desativado."
            : "Não foi possível verificar a desativação. Consulte os recursos do Windows.");
    }
}
