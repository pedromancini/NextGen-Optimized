package com.nextgen.optimizer.tweaks;

import com.sun.jna.platform.win32.WinReg;

import java.util.Map;

/**
 * Changes a Windows service start type through its registry key, which is
 * exactly what {@code sc config} does. Services that are not installed are
 * reported as unavailable and never created.
 */
public class ServiceAction extends RegistryAction {

    public static final int AUTOMATIC = 2;
    public static final int MANUAL = 3;
    public static final int DISABLED = 4;

    private final String service;
    private final int startType;

    public ServiceAction(String service, int startType) {
        super(WinReg.HKEY_LOCAL_MACHINE, "SYSTEM\\CurrentControlSet\\Services\\" + service,
                () -> "Start", startType, true);
        this.service = service;
        this.startType = startType;
    }

    @Override
    public boolean apply(TweakContext ctx) {
        boolean ok = super.apply(ctx);
        if (ok && startType == DISABLED) {
            // Best effort: stop it now so the benefit is immediate; start type is what matters.
            ctx.shell().runProcess(20, "sc.exe", "stop", service);
        }
        return ok;
    }

    @Override
    public boolean revert(TweakContext ctx, Map<String, String> original) {
        boolean ok = super.revert(ctx, original);
        if (ok && "2".equals(original.get("value"))) {
            ctx.shell().runProcess(20, "sc.exe", "start", service);
        }
        return ok;
    }

    @Override
    public String describe() {
        String type = switch (startType) {
            case AUTOMATIC -> "Automático";
            case MANUAL -> "Manual";
            default -> "Desativado";
        };
        return "Serviço " + service + " → " + type;
    }
}
