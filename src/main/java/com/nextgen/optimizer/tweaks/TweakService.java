package com.nextgen.optimizer.tweaks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Applies and reverts tweaks with three guarantees:
 * <ol>
 *   <li>originals are persisted <em>before</em> any write (no save → no change);</li>
 *   <li>a partially failed apply is rolled back action by action;</li>
 *   <li>revert restores the captured originals in reverse order.</li>
 * </ol>
 */
public class TweakService {

    public enum Status { APPLIED, NOT_APPLIED, PARTIAL, UNAVAILABLE }

    public record Result(Tweak tweak, boolean success, boolean changed, String message) {}

    private final TweakContext ctx;
    private final TweakStore store;
    private final List<Tweak> catalog;

    public TweakService(TweakContext ctx, TweakStore store, List<Tweak> catalog) {
        this.ctx = ctx;
        this.store = store;
        this.catalog = List.copyOf(catalog);
    }

    public List<Tweak> catalog() { return catalog; }

    public TweakContext context() { return ctx; }

    public Tweak find(String id) {
        return catalog.stream().filter(t -> t.id().equals(id)).findFirst().orElse(null);
    }

    public Status status(Tweak tweak) {
        int available = 0;
        int applied = 0;
        for (TweakAction action : tweak.actions()) {
            if (!safe(() -> action.isAvailable(ctx))) continue;
            available++;
            if (safe(() -> action.isApplied(ctx))) applied++;
        }
        if (available == 0) return Status.UNAVAILABLE;
        if (applied == available) return Status.APPLIED;
        return applied == 0 ? Status.NOT_APPLIED : Status.PARTIAL;
    }

    /** True when NextGen X holds the originals for this tweak and can revert it. */
    public boolean canRevert(Tweak tweak) {
        return store.has(tweak.id());
    }

    public synchronized Result apply(Tweak tweak) {
        List<TweakAction> actions = new ArrayList<>();
        for (TweakAction a : tweak.actions()) {
            if (safe(() -> a.isAvailable(ctx))) actions.add(a);
        }
        if (actions.isEmpty()) {
            return new Result(tweak, true, false, "Não se aplica a este PC.");
        }
        if (actions.stream().allMatch(a -> safe(() -> a.isApplied(ctx)))) {
            return new Result(tweak, true, false, "Já estava aplicado.");
        }

        // 1. Persist originals first. Keep the very first capture if one already exists.
        TweakStore.Entry entry = store.get(tweak.id());
        if (entry == null) {
            entry = new TweakStore.Entry();
            entry.appliedAt = System.currentTimeMillis();
            for (TweakAction a : tweak.actions()) {
                Map<String, String> original;
                try {
                    original = safe(() -> a.isAvailable(ctx)) ? a.capture(ctx) : Map.of("skipped", "true");
                } catch (Exception e) {
                    return new Result(tweak, false, false, "Falha ao ler o estado atual. Nada foi alterado.");
                }
                entry.originals.add(original);
            }
            if (!store.put(tweak.id(), entry)) {
                return new Result(tweak, false, false, "Não foi possível salvar o backup. Nada foi alterado.");
            }
        }

        // 2. Apply, rolling back on the first failure.
        List<Integer> done = new ArrayList<>();
        for (int i = 0; i < tweak.actions().size(); i++) {
            TweakAction a = tweak.actions().get(i);
            if (!actions.contains(a) || safe(() -> a.isApplied(ctx))) continue;
            boolean ok;
            try {
                ok = a.apply(ctx);
            } catch (Exception e) {
                ok = false;
            }
            if (!ok) {
                rollback(tweak, entry, done);
                return new Result(tweak, false, false,
                        "O Windows recusou a alteração; as mudanças deste ajuste foram desfeitas.");
            }
            done.add(i);
        }
        return new Result(tweak, true, true, tweak.requiresRestart()
                ? "Aplicado. Reinicie o PC para efeito completo." : "Aplicado com sucesso.");
    }

    public synchronized Result revert(Tweak tweak) {
        TweakStore.Entry entry = store.get(tweak.id());
        if (entry == null) {
            return new Result(tweak, false, false,
                    "Este ajuste já estava assim antes do NextGen X; não há valor original para restaurar.");
        }
        boolean ok = true;
        for (int i = tweak.actions().size() - 1; i >= 0; i--) {
            if (i >= entry.originals.size()) continue;
            Map<String, String> original = entry.originals.get(i);
            if ("true".equals(original.get("skipped"))) continue;
            try {
                ok &= tweak.actions().get(i).revert(ctx, original);
            } catch (Exception e) {
                ok = false;
            }
        }
        if (ok) store.remove(tweak.id());
        return new Result(tweak, ok, ok, ok
                ? (tweak.requiresRestart() ? "Revertido. Reinicie o PC para concluir." : "Configuração original restaurada.")
                : "Restauração incompleta. Tente novamente como administrador.");
    }

    public List<Tweak> tweaksFor(Tweak.Profile profile) {
        return planFirst(catalog.stream().filter(t -> profile.includes(t.profile())).toList());
    }

    public List<Result> applyAll(List<Tweak> tweaks, Consumer<Result> progress) {
        List<Result> results = new ArrayList<>();
        for (Tweak t : planFirst(tweaks)) {
            Result r;
            try {
                r = apply(t);
            } catch (Exception e) {
                r = new Result(t, false, false, "Erro inesperado: " + e.getMessage());
            }
            results.add(r);
            if (progress != null) progress.accept(r);
        }
        return results;
    }

    /**
     * Power settings are written to the active plan, so the plan switch must
     * happen before them; otherwise they would land on the plan being replaced.
     */
    public static List<Tweak> planFirst(List<Tweak> tweaks) {
        List<Tweak> ordered = new ArrayList<>(tweaks);
        ordered.sort(java.util.Comparator.comparingInt(
                t -> t.actions().stream().anyMatch(a -> a instanceof PowerPlanAction) ? 0 : 1));
        return ordered;
    }

    /** Reverts every tweak NextGen X applied, newest first. */
    public List<Result> revertAll(Consumer<Result> progress) {
        List<Result> results = new ArrayList<>();
        List<String> ids = new ArrayList<>(store.ids());
        java.util.Collections.reverse(ids);
        for (String id : ids) {
            Tweak t = find(id);
            if (t == null) continue;
            Result r = revert(t);
            results.add(r);
            if (progress != null) progress.accept(r);
        }
        return results;
    }

    public int appliedByNextGenCount() {
        return (int) store.ids().stream().filter(id -> find(id) != null).count();
    }

    private void rollback(Tweak tweak, TweakStore.Entry entry, List<Integer> done) {
        for (int k = done.size() - 1; k >= 0; k--) {
            int i = done.get(k);
            try {
                tweak.actions().get(i).revert(ctx, entry.originals.get(i));
            } catch (Exception ignored) {}
        }
        // Only forget the originals if nothing from an earlier apply is still active.
        if (tweak.actions().stream().noneMatch(a -> safe(() -> a.isAvailable(ctx) && a.isApplied(ctx)))) {
            store.remove(tweak.id());
        }
    }

    private static boolean safe(java.util.function.BooleanSupplier check) {
        try {
            return check.getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }
}
