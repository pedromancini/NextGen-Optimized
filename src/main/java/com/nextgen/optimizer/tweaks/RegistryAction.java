package com.nextgen.optimizer.tweaks;

import com.sun.jna.platform.win32.WinReg;

import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Sets a registry value (DWORD or REG_SZ). The original value, its type, or
 * its absence is captured so revert restores the exact previous state,
 * including deleting values that did not exist before.
 */
public class RegistryAction implements TweakAction {

    protected final WinReg.HKEY root;
    protected final String path;
    private final Supplier<String> name;
    private final Object desired;
    private final boolean requireKey;

    public RegistryAction(WinReg.HKEY root, String path, String name, Object desired) {
        this(root, path, () -> name, desired, false);
    }

    /**
     * @param name       supplier for value names computed at runtime (e.g. an
     *                   executable path); returning {@code null} makes the action unavailable.
     * @param requireKey when true the action is unavailable if the key does not exist.
     */
    public RegistryAction(WinReg.HKEY root, String path, Supplier<String> name, Object desired, boolean requireKey) {
        if (!(desired instanceof Integer) && !(desired instanceof String)) {
            throw new IllegalArgumentException("Only DWORD (Integer) and REG_SZ (String) are supported");
        }
        this.root = root;
        this.path = path;
        this.name = name;
        this.desired = desired;
        this.requireKey = requireKey;
    }

    public static RegistryAction dword(WinReg.HKEY root, String path, String name, int value) {
        return new RegistryAction(root, path, name, value);
    }

    public static RegistryAction string(WinReg.HKEY root, String path, String name, String value) {
        return new RegistryAction(root, path, name, value);
    }

    protected String valueName() {
        try {
            return name.get();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isAvailable(TweakContext ctx) {
        if (valueName() == null) return false;
        return !requireKey || ctx.registry().keyExists(root, path);
    }

    @Override
    public boolean isApplied(TweakContext ctx) {
        String n = valueName();
        if (n == null) return false;
        return matches(ctx.registry().getRawValue(root, path, n), desired);
    }

    static boolean matches(Object current, Object desired) {
        if (current == null) return false;
        if (desired instanceof Integer d) {
            return current instanceof Integer c && c.intValue() == d.intValue();
        }
        return desired.toString().equalsIgnoreCase(String.valueOf(current).trim());
    }

    @Override
    public Map<String, String> capture(TweakContext ctx) {
        Map<String, String> m = new HashMap<>();
        String n = valueName();
        m.put("name", n);
        Object current = n == null ? null : ctx.registry().getRawValue(root, path, n);
        m.put("existed", Boolean.toString(current != null));
        if (current instanceof Integer i) {
            m.put("type", "DWORD");
            m.put("value", Integer.toString(i));
        } else if (current instanceof Long l) {
            m.put("type", "QWORD");
            m.put("value", Long.toString(l));
        } else if (current instanceof byte[] bytes) {
            m.put("type", "BINARY");
            m.put("value", HexFormat.of().formatHex(bytes));
        } else if (current != null) {
            m.put("type", "SZ");
            m.put("value", String.valueOf(current));
        }
        return m;
    }

    @Override
    public boolean apply(TweakContext ctx) {
        String n = valueName();
        if (n == null) return false;
        return desired instanceof Integer i
                ? ctx.registry().setIntValue(root, path, n, i)
                : ctx.registry().setStringValue(root, path, n, desired.toString());
    }

    @Override
    public boolean revert(TweakContext ctx, Map<String, String> original) {
        String n = original.getOrDefault("name", valueName());
        if (n == null) return true;
        if (!Boolean.parseBoolean(original.get("existed"))) {
            return ctx.registry().deleteValue(root, path, n);
        }
        String value = Objects.toString(original.get("value"), "");
        return switch (Objects.toString(original.get("type"), "SZ")) {
            case "DWORD" -> ctx.registry().setIntValue(root, path, n, Integer.parseInt(value));
            case "QWORD" -> ctx.registry().setLongValue(root, path, n, Long.parseLong(value));
            case "BINARY" -> ctx.registry().setBinaryValue(root, path, n, HexFormat.of().parseHex(value));
            default -> ctx.registry().setStringValue(root, path, n, value);
        };
    }

    @Override
    public String describe() {
        String n = valueName();
        String hive = WinReg.HKEY_LOCAL_MACHINE.equals(root) ? "HKLM" : "HKCU";
        String shown = desired instanceof Integer i ? String.format("0x%X", i) : "\"" + desired + "\"";
        return hive + "\\" + path + "\\" + (n == null ? "?" : n) + " = " + shown;
    }
}
