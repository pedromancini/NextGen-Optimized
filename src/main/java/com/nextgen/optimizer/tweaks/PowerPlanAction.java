package com.nextgen.optimizer.tweaks;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Activates the Ultimate Performance plan (creating it from the hidden
 * template when needed) or falls back to High Performance. Revert re-activates
 * the plan that was active before; no plan is ever deleted.
 */
public class PowerPlanAction implements TweakAction {

    public static final String HIGH_PERFORMANCE = "{8C5E7FDA-E8BF-4A96-9A85-A6E23A8C635C}";
    public static final String ULTIMATE_TEMPLATE = "{E9A42B02-D5DF-448D-AA00-03F14749EB61}";

    private static final Pattern GUID = Pattern.compile(
            "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");
    private static final Pattern ULTIMATE_LINE = Pattern.compile(
            "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})[^\\r\\n]*\\((?:Ultimate|Desempenho M.{1,3}ximo|NextGen)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HIGH_LINE = Pattern.compile(
            "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})[^\\r\\n]*\\((?:High performance|Alto desempenho)",
            Pattern.CASE_INSENSITIVE);

    @Override
    public boolean isApplied(TweakContext ctx) {
        String active = normalize(ctx.power().activeScheme());
        if (active == null) return false;
        if (active.equals(HIGH_PERFORMANCE) || active.equals(ULTIMATE_TEMPLATE)) return true;
        String list = ctx.power().powercfg("/list");
        return active.equals(find(ULTIMATE_LINE, list)) || active.equals(find(HIGH_LINE, list));
    }

    @Override
    public Map<String, String> capture(TweakContext ctx) {
        Map<String, String> m = new HashMap<>();
        m.put("scheme", normalize(ctx.power().activeScheme()));
        return m;
    }

    @Override
    public boolean apply(TweakContext ctx) {
        String list = ctx.power().powercfg("/list");
        String ultimate = find(ULTIMATE_LINE, list);
        if (ultimate == null) {
            String created = find(GUID, ctx.power().powercfg("-duplicatescheme", strip(ULTIMATE_TEMPLATE)));
            if (created != null) {
                ctx.power().powercfg("-changename", strip(created), "NextGen X Ultimate",
                        "Plano Desempenho Máximo criado pelo NextGen X");
                ultimate = created;
            }
        }
        if (ultimate != null && ctx.power().setActiveScheme(ultimate)) return true;
        String high = find(HIGH_LINE, list);
        if (high != null && ctx.power().setActiveScheme(high)) return true;
        // Modern Standby systems hide these plans; nothing is changed in that case.
        return ctx.power().setActiveScheme(HIGH_PERFORMANCE);
    }

    @Override
    public boolean revert(TweakContext ctx, Map<String, String> original) {
        String scheme = original.get("scheme");
        return scheme == null || ctx.power().setActiveScheme(scheme);
    }

    @Override
    public String describe() {
        return "Ativa o plano Desempenho Máximo (ou Alto Desempenho)";
    }

    private static String find(Pattern pattern, String text) {
        if (text == null) return null;
        Matcher m = pattern.matcher(text);
        return m.find() ? normalize(m.group(1)) : null;
    }

    private static String normalize(String guid) {
        if (guid == null || guid.isBlank()) return null;
        String g = guid.trim().toUpperCase();
        return g.startsWith("{") ? g : "{" + g + "}";
    }

    private static String strip(String guid) {
        return guid.replace("{", "").replace("}", "");
    }
}
