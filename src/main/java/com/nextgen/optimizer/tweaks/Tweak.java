package com.nextgen.optimizer.tweaks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A reversible system optimization made of one or more {@link TweakAction}s.
 * Tweaks are immutable descriptions; state lives in the registry / power
 * scheme and in the {@link TweakStore}.
 */
public final class Tweak {

    public enum Category {
        GAMES("Jogos & FPS", "mdi2g-gamepad-variant-outline"),
        CS2("Counter-Strike 2", "mdi2t-target"),
        LATENCY("Latência & Input", "mdi2m-mouse"),
        NETWORK("Rede", "mdi2w-wifi"),
        MEMORY("Memória & Disco", "mdi2m-memory"),
        POWER("Energia & CPU", "mdi2l-lightning-bolt-outline"),
        SERVICES("Serviços", "mdi2c-cog-transfer-outline"),
        SYSTEM("Windows & Interface", "mdi2m-microsoft-windows");

        public final String label;
        public final String icon;

        Category(String label, String icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    public enum Risk {
        SAFE("Seguro", "risk-safe"),
        MODERATE("Moderado", "risk-moderate"),
        ADVANCED("Avançado", "risk-advanced");

        public final String label;
        public final String styleClass;

        Risk(String label, String styleClass) {
            this.label = label;
            this.styleClass = styleClass;
        }
    }

    /**
     * Optimization profiles. Each tweak declares the lowest profile that
     * includes it; higher profiles include everything below them.
     */
    public enum Profile {
        SAFE("Essencial", "Somente ajustes seguros, sem efeitos colaterais perceptíveis."),
        GAMER("Gamer", "Essencial + energia, prioridades de jogo e Windows mais leve."),
        COMPETITIVE("Competitivo CS2", "Gamer + latência mínima, CPU sempre em clock máximo e ajustes do CS2.");

        public final String label;
        public final String description;

        Profile(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public boolean includes(Profile tweakProfile) {
            return tweakProfile != null && tweakProfile.ordinal() <= ordinal();
        }
    }

    private final String id;
    private final Category category;
    private final String title;
    private final String description;
    private final String warning;
    private final Risk risk;
    private final Profile profile;
    private final boolean requiresRestart;
    private final List<String> impact;
    private final List<TweakAction> actions;

    private Tweak(Builder b) {
        this.id = b.id;
        this.category = b.category;
        this.title = b.title;
        this.description = b.description;
        this.warning = b.warning;
        this.risk = b.risk;
        this.profile = b.profile;
        this.requiresRestart = b.requiresRestart;
        this.impact = Collections.unmodifiableList(new ArrayList<>(b.impact));
        this.actions = Collections.unmodifiableList(new ArrayList<>(b.actions));
    }

    public static Builder builder(String id, Category category) {
        return new Builder(id, category);
    }

    public String id() { return id; }
    public Category category() { return category; }
    public String title() { return title; }
    public String description() { return description; }
    public String warning() { return warning; }
    public Risk risk() { return risk; }
    /** Lowest profile that includes this tweak, or {@code null} for manual-only tweaks. */
    public Profile profile() { return profile; }
    public boolean requiresRestart() { return requiresRestart; }
    public List<String> impact() { return impact; }
    public List<TweakAction> actions() { return actions; }

    public String searchText() {
        return (title + " " + description + " " + category.label + " " + String.join(" ", impact)).toLowerCase();
    }

    public static final class Builder {
        private final String id;
        private final Category category;
        private String title = "";
        private String description = "";
        private String warning;
        private Risk risk = Risk.SAFE;
        private Profile profile;
        private boolean requiresRestart;
        private final List<String> impact = new ArrayList<>();
        private final List<TweakAction> actions = new ArrayList<>();

        private Builder(String id, Category category) {
            this.id = id;
            this.category = category;
        }

        public Builder title(String v) { title = v; return this; }
        public Builder description(String v) { description = v; return this; }
        public Builder warning(String v) { warning = v; return this; }
        public Builder risk(Risk v) { risk = v; return this; }
        public Builder profile(Profile v) { profile = v; return this; }
        public Builder restart() { requiresRestart = true; return this; }
        public Builder impact(String... tags) { impact.addAll(List.of(tags)); return this; }
        public Builder action(TweakAction a) { actions.add(a); return this; }
        public Builder actions(List<? extends TweakAction> a) { actions.addAll(a); return this; }

        public Tweak build() {
            if (actions.isEmpty()) throw new IllegalStateException("Tweak " + id + " has no actions");
            return new Tweak(this);
        }
    }
}
