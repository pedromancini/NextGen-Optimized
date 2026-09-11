import com.nextgen.optimizer.services.*;
import com.sun.jna.platform.win32.WinReg;
import java.util.*;

/** Standalone checks use memory-backed registry/backup doubles, never Windows settings. */
public class PrivacyChecks {
    static class Registry extends RegistryService {
        Map<String, Integer> values = new HashMap<>();
        boolean fail;
        int writes;
        public int getIntValue(WinReg.HKEY root, String path, String name, int fallback) {
            return values.getOrDefault(path + name, fallback);
        }
        public boolean setIntValue(WinReg.HKEY root, String path, String name, int value) {
            writes++;
            if (fail) return false;
            values.put(path + name, value);
            return true;
        }
    }
    static class Backups extends BackupService {
        boolean fail;
        int saves;
        BackupSnapshot saved;
        public BackupSnapshot loadLatestBackup(String label) { return saved; }
        public void addEntry(BackupSnapshot s, String p, String n, WinReg.HKEY r) {}
        public boolean saveBackup(BackupSnapshot s) {
            if (fail) return false;
            saved = s; saves++; return true;
        }
    }
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        Registry r = new Registry(); Backups b = new Backups();
        PrivacyService s = new PrivacyService(r, b, new PowerShellService());
        var option = s.options().get(0);
        check(s.state(option) == PrivacyService.State.UNKNOWN, "Absent values must not report enabled/disabled");
        b.fail = true;
        check(!s.apply(option).success() && r.writes == 0, "Backup failure must block writes");
        b.fail = false; r.fail = true;
        check(!s.apply(option).success(), "Failed writes must not report success");
        r.fail = false;
        check(s.apply(option).success(), "Apply should verify preferences");
        check(s.apply(option).success() && b.saves == 1, "Repeated apply must preserve original backup");
        check(PrivacyService.parseRecallState("DisabledWithPayloadRemoved") == PrivacyService.RecallState.DISABLED, "Removed payload");
        check(PrivacyService.parseRecallState("DisablePending") == PrivacyService.RecallState.PENDING, "Pending restart");
        check(PrivacyService.parseRecallState("Access denied") == PrivacyService.RecallState.UNKNOWN, "Errors must not imply absence");
        System.out.println("PASS: privacy backup guard, failed writes, idempotence, state parsing");
    }
}
