package com.nextgen.optimizer.tweaks;

import com.nextgen.optimizer.nativeapi.WinNative;
import com.nextgen.optimizer.services.PowerShellService;
import com.nextgen.optimizer.services.RegistryService;

/**
 * Dependencies shared by tweak actions. Kept behind small interfaces so the
 * engine can be verified with in-memory doubles without touching Windows.
 */
public class TweakContext {

    public interface PowerAccess {
        String activeScheme();
        boolean setActiveScheme(String guid);
        Integer readAc(String scheme, String subGroup, String setting);
        boolean writeAc(String scheme, String subGroup, String setting, int value);
        /** Raw {@code powercfg} invocation; returns stdout or "" on failure. */
        String powercfg(String... args);
    }

    public static PowerAccess nativePower(PowerShellService shell) {
        return new PowerAccess() {
            public String activeScheme() { return WinNative.activePowerScheme(); }
            public boolean setActiveScheme(String guid) { return WinNative.setActivePowerScheme(guid); }
            public Integer readAc(String scheme, String sub, String setting) { return WinNative.readAcValue(scheme, sub, setting); }
            public boolean writeAc(String scheme, String sub, String setting, int value) {
                return WinNative.writeAcValue(scheme, sub, setting, value);
            }
            public String powercfg(String... args) {
                String[] command = new String[args.length + 1];
                command[0] = "powercfg.exe";
                System.arraycopy(args, 0, command, 1, args.length);
                return shell.runProcess(30, command).output();
            }
        };
    }

    private final RegistryService registry;
    private final PowerAccess power;
    private final PowerShellService shell;

    public TweakContext(RegistryService registry, PowerAccess power, PowerShellService shell) {
        this.registry = registry;
        this.power = power;
        this.shell = shell;
    }

    public RegistryService registry() { return registry; }
    public PowerAccess power() { return power; }
    public PowerShellService shell() { return shell; }
}
