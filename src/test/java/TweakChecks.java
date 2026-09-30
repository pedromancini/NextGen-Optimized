import com.nextgen.optimizer.services.Cs2ConfigService;
import com.nextgen.optimizer.services.PowerShellService;
import com.nextgen.optimizer.services.RegistryService;
import com.nextgen.optimizer.services.SteamLocator;
import com.nextgen.optimizer.tweaks.*;
import com.sun.jna.platform.win32.WinReg;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Engine checks with in-memory registry/power doubles; never touches Windows settings. */
public class TweakChecks {

    static class Registry extends RegistryService {
        final Map<String, Object> values = new HashMap<>();
        final Set<String> keys = new HashSet<>();
        String failOn;
        int writes;

        static String k(WinReg.HKEY root, String path, String name) {
            return (WinReg.HKEY_LOCAL_MACHINE.equals(root) ? "HKLM" : "HKCU") + "\\" + path + "\\" + name;
        }
        @Override public Object getRawValue(WinReg.HKEY root, String path, String name) { return values.get(k(root, path, name)); }
        @Override public int getIntValue(WinReg.HKEY root, String path, String name, int def) {
            return values.get(k(root, path, name)) instanceof Integer i ? i : def;
        }
        @Override public String getStringValue(WinReg.HKEY root, String path, String name, String def) {
            return values.get(k(root, path, name)) instanceof String s ? s : def;
        }
        @Override public String[] getSubKeys(WinReg.HKEY root, String path) { return new String[0]; }
        @Override public boolean keyExists(WinReg.HKEY root, String path) { return keys.contains(path); }
        @Override public boolean setIntValue(WinReg.HKEY root, String path, String name, int v) { return put(root, path, name, v); }
        @Override public boolean setStringValue(WinReg.HKEY root, String path, String name, String v) { return put(root, path, name, v); }
        @Override public boolean deleteValue(WinReg.HKEY root, String path, String name) {
            values.remove(k(root, path, name)); writes++; return true;
        }
        boolean put(WinReg.HKEY root, String path, String name, Object v) {
            writes++;
            if (name.equals(failOn)) return false;
            values.put(k(root, path, name), v);
            return true;
        }
    }

    static class Power implements TweakContext.PowerAccess {
        String active = "{381B4222-F694-41F0-9685-FF5BB260DF2E}";
        final Map<String, Integer> ac = new HashMap<>();
        public String activeScheme() { return active; }
        public boolean setActiveScheme(String g) { active = g; return true; }
        public Integer readAc(String s, String sub, String set) { return ac.getOrDefault(s + sub + set, 5); }
        public boolean writeAc(String s, String sub, String set, int v) { ac.put(s + sub + set, v); return true; }
        public String powercfg(String... args) { return ""; }
    }

    static class Shell extends PowerShellService {
        @Override public CommandResult runProcess(long t, String... c) { return executeResult(""); }
    }

    static class MemoryStore extends TweakStore {
        boolean fail;
        MemoryStore(Path p) { super(p); }
        @Override protected boolean save() { return !fail; }
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("nextgen-checks");
        Registry reg = new Registry();
        Power power = new Power();
        TweakContext ctx = new TweakContext(reg, power, new Shell());

        // ── Catalog sanity ──
        List<Tweak> catalog = TweakCatalog.build(reg, new SteamLocator(reg) {
            @Override public String cs2ExecutableString() { return "D:\\Steam\\cs2.exe"; }
        });
        Set<String> ids = new HashSet<>();
        for (Tweak t : catalog) check(ids.add(t.id()), "Duplicate tweak id " + t.id());
        check(catalog.size() >= 45, "Catalog should offer many options, got " + catalog.size());
        Set<String> targets = new HashSet<>();
        for (Tweak t : catalog)
            for (TweakAction a : t.actions())
                if (!(a instanceof PowerPlanAction) && !(a instanceof HibernateAction))
                    check(targets.add(a.describe().replaceAll(" = .*", "").replaceAll(" → .*", "")),
                            "Two tweaks write the same target: " + a.describe());
        check(catalog.stream().filter(t -> t.risk() == Tweak.Risk.ADVANCED).allMatch(t -> t.profile() == null),
                "Advanced tweaks must never be part of an automatic profile");

        // ── Apply → revert restores absent, DWORD and string values exactly ──
        MemoryStore store = new MemoryStore(dir.resolve("state.json"));
        TweakService service = new TweakService(ctx, store, catalog);
        Tweak dvr = service.find("game-dvr-off");
        reg.values.put("HKCU\\System\\GameConfigStore\\GameDVR_Enabled", 1);
        check(service.status(dvr) == TweakService.Status.NOT_APPLIED, "DVR initially not applied");
        check(service.apply(dvr).success(), "DVR apply");
        check(service.status(dvr) == TweakService.Status.APPLIED, "DVR applied");
        check(service.revert(dvr).success(), "DVR revert");
        check(Integer.valueOf(1).equals(reg.values.get("HKCU\\System\\GameConfigStore\\GameDVR_Enabled")), "Original DWORD restored");
        check(!reg.values.containsKey("HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\GameDVR\\AllowGameDVR"), "Absent value deleted on revert");
        check(!service.canRevert(dvr), "Store cleared after revert");

        Tweak mouse = service.find("mouse-accel-off");
        reg.values.put("HKCU\\Control Panel\\Mouse\\MouseSpeed", "1");
        service.apply(mouse);
        service.revert(mouse);
        check("1".equals(reg.values.get("HKCU\\Control Panel\\Mouse\\MouseSpeed")), "Original string restored");

        // ── Store failure blocks every write ──
        store.fail = true;
        int writesBefore = reg.writes;
        check(!service.apply(service.find("menu-delay")).success() && reg.writes == writesBefore,
                "Backup failure must block writes");
        store.fail = false;

        // ── Partial failure rolls back ──
        reg.failOn = "Scheduling Category";
        Tweak mmcss = service.find("mmcss-games");
        check(!service.apply(mmcss).success(), "Failing write must report failure");
        check(!reg.values.containsKey("HKLM\\" + "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Multimedia\\SystemProfile\\Tasks\\Games\\GPU Priority"),
                "Earlier writes rolled back");
        reg.failOn = null;

        // ── Re-apply keeps the very first originals ──
        Tweak menu = service.find("menu-delay");
        reg.values.put("HKCU\\Control Panel\\Desktop\\MenuShowDelay", "400");
        service.apply(menu);
        reg.values.put("HKCU\\Control Panel\\Desktop\\MenuShowDelay", "200"); // something else changed it
        service.apply(menu);
        service.revert(menu);
        check("400".equals(reg.values.get("HKCU\\Control Panel\\Desktop\\MenuShowDelay")), "First original preserved");

        // ── Services that are not installed are skipped, never created ──
        Tweak fax = service.find("svc-fax");
        check(service.status(fax) == TweakService.Status.UNAVAILABLE, "Missing service unavailable");
        check(service.apply(fax).success() && !reg.values.keySet().stream().anyMatch(k -> k.contains("Services\\Fax")),
                "Missing service untouched");

        // ── Power settings revert to the scheme they were captured from ──
        Tweak cpu = service.find("cpu-min-100");
        String balanced = power.active;
        service.apply(cpu);
        power.active = "{OTHER}";
        service.revert(cpu);
        check(power.ac.get(balanced + "54533251-82be-4824-96c1-47b60b740d00893dee8e-2bef-41e0-89c6-b55d0929964c") == 5,
                "Power index restored on original scheme");

        // ── Profiles are nested ──
        check(service.tweaksFor(Tweak.Profile.COMPETITIVE).containsAll(service.tweaksFor(Tweak.Profile.GAMER)), "Competitive ⊇ Gamer");
        check(service.tweaksFor(Tweak.Profile.GAMER).containsAll(service.tweaksFor(Tweak.Profile.SAFE)), "Gamer ⊇ Essential");
        check(service.tweaksFor(Tweak.Profile.GAMER).get(0).id().equals("power-plan"),
                "Power plan must switch before per-plan power settings are written");

        // ── Persistence survives a restart ──
        TweakStore disk = new TweakStore(dir.resolve("disk.json"));
        TweakService s1 = new TweakService(ctx, disk, catalog);
        reg.values.put("HKCU\\Control Panel\\Desktop\\MenuShowDelay", "400");
        s1.apply(menu);
        TweakService s2 = new TweakService(ctx, new TweakStore(dir.resolve("disk.json")), catalog);
        check(s2.canRevert(menu) && s2.revert(menu).success(), "Originals reloaded from disk");
        check("400".equals(reg.values.get("HKCU\\Control Panel\\Desktop\\MenuShowDelay")), "Restored after reload");

        // ── autoexec block keeps the player's lines ──
        String user = "bind \"x\" \"noclip\"\r\nsensitivity 1.2\r\n";
        String merged = Cs2ConfigService.mergeBlock(user, List.of("fps_max 0"));
        check(merged.contains("sensitivity 1.2") && merged.contains("fps_max 0"), "Block appended, user lines kept");
        String again = Cs2ConfigService.mergeBlock(merged, List.of("fps_max 400"));
        check(!again.contains("fps_max 0") && again.contains("fps_max 400")
                && again.indexOf(Cs2ConfigService.BLOCK_START) == again.lastIndexOf(Cs2ConfigService.BLOCK_START), "Block replaced once");
        check(Cs2ConfigService.mergeBlock(again, null).equals(user), "Removing the block restores the file");

        System.out.println("PASS: " + catalog.size() + " tweaks; backup-first, rollback, exact revert, services, power schemes, profiles, persistence, autoexec");
    }
}
