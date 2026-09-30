package com.nextgen.optimizer.tweaks;

import com.sun.jna.platform.win32.WinReg;

import java.util.HashMap;
import java.util.Map;

/**
 * Turns hibernation off through {@code powercfg -h off}, which also deletes
 * hiberfil.sys. Revert turns it back on only if it was on before.
 */
public class HibernateAction implements TweakAction {

    private static final String POWER_KEY = "SYSTEM\\CurrentControlSet\\Control\\Power";

    @Override
    public boolean isApplied(TweakContext ctx) {
        return ctx.registry().getIntValue(WinReg.HKEY_LOCAL_MACHINE, POWER_KEY, "HibernateEnabled", 1) == 0;
    }

    @Override
    public Map<String, String> capture(TweakContext ctx) {
        Map<String, String> m = new HashMap<>();
        m.put("enabled", Boolean.toString(!isApplied(ctx)));
        return m;
    }

    @Override
    public boolean apply(TweakContext ctx) {
        ctx.power().powercfg("-h", "off");
        return isApplied(ctx);
    }

    @Override
    public boolean revert(TweakContext ctx, Map<String, String> original) {
        if (!Boolean.parseBoolean(original.get("enabled"))) return true;
        ctx.power().powercfg("-h", "on");
        return !isApplied(ctx);
    }

    @Override
    public String describe() {
        return "powercfg -h off (remove hiberfil.sys)";
    }
}
