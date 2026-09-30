package com.nextgen.optimizer.tweaks;

import java.util.HashMap;
import java.util.Map;

/**
 * Sets an AC (plugged-in) power setting on the active power scheme. Battery
 * (DC) values are never touched. Revert writes the original index back to the
 * same scheme it was captured from, even if the active plan changed since.
 */
public class PowerSettingAction implements TweakAction {

    private final String subGroup;
    private final String setting;
    private final int desired;
    private final String label;

    public PowerSettingAction(String subGroup, String setting, int desired, String label) {
        this.subGroup = subGroup;
        this.setting = setting;
        this.desired = desired;
        this.label = label;
    }

    @Override
    public boolean isAvailable(TweakContext ctx) {
        String scheme = ctx.power().activeScheme();
        return scheme != null && ctx.power().readAc(scheme, subGroup, setting) != null;
    }

    @Override
    public boolean isApplied(TweakContext ctx) {
        String scheme = ctx.power().activeScheme();
        if (scheme == null) return false;
        Integer value = ctx.power().readAc(scheme, subGroup, setting);
        return value != null && value == desired;
    }

    @Override
    public Map<String, String> capture(TweakContext ctx) {
        Map<String, String> m = new HashMap<>();
        String scheme = ctx.power().activeScheme();
        m.put("scheme", scheme);
        Integer value = scheme == null ? null : ctx.power().readAc(scheme, subGroup, setting);
        if (value != null) m.put("value", Integer.toString(value));
        return m;
    }

    @Override
    public boolean apply(TweakContext ctx) {
        String scheme = ctx.power().activeScheme();
        return scheme != null && ctx.power().writeAc(scheme, subGroup, setting, desired);
    }

    @Override
    public boolean revert(TweakContext ctx, Map<String, String> original) {
        String scheme = original.get("scheme");
        String value = original.get("value");
        if (scheme == null || value == null) return true;
        return ctx.power().writeAc(scheme, subGroup, setting, Integer.parseInt(value));
    }

    @Override
    public String describe() {
        return "Plano de energia ativo (na tomada): " + label + " = " + desired;
    }
}
