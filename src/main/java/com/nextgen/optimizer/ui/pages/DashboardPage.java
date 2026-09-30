package com.nextgen.optimizer.ui.pages;

import com.nextgen.optimizer.App;
import com.nextgen.optimizer.core.NotificationManager;
import com.nextgen.optimizer.model.SystemSnapshot;
import com.nextgen.optimizer.services.CleanupService;
import com.nextgen.optimizer.services.MemoryService;
import com.nextgen.optimizer.tweaks.Tweak;
import com.nextgen.optimizer.tweaks.TweakService;
import com.nextgen.optimizer.ui.components.*;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.List;
import java.util.Locale;

/**
 * Home: optimization score, one-click entry to the full optimization, live
 * hardware gauges and quick actions that report what they actually did.
 */
public class DashboardPage extends VBox {

    private final App app;
    private final ProgressRing scoreRing = new ProgressRing(132);
    private final Label scoreTitle = new Label("Analisando seu PC…");
    private final Label scoreDetail = new Label("Verificando os ajustes recomendados para o perfil Gamer.");
    private final Label boosterValue = new Label("--");
    private final Label cs2Value = new Label("--");
    private final Label tweaksValue = new Label("--");
    private Gauge cpuGauge, gpuGauge, ramGauge;
    private Label cpuSub, gpuSub, ramSub;
    private Label pingValue, cpuTemp, gpuTemp, diskValue;
    private final Label specCpu = new Label("--"), specGpu = new Label("--"), specRam = new Label("--");

    public DashboardPage(App app) {
        this.app = app;
        getStyleClass().add("page-container");
        setSpacing(18);

        Label live = new Label("● AO VIVO");
        live.getStyleClass().add("live-badge");
        getChildren().addAll(
                Ui.pageHeader("mdi2v-view-dashboard-outline", "Painel",
                        "Estado do seu PC em tempo real e o caminho mais curto para mais FPS.", live),
                buildHero(),
                buildGauges(),
                Ui.sectionLabel("Ações rápidas"),
                buildQuickActions(),
                buildSystemCard());

        app.getSystemInfoService().snapshotProperty().addListener((o, a, s) -> {
            if (s != null) update(s);
        });
        update(app.getSystemInfoService().getLatestSnapshot());
        refreshScore();
    }

    // ── Hero ────────────────────────────────────────────────────────

    private Node buildHero() {
        scoreRing.setPositive(true);
        scoreRing.setCenterText("--");
        scoreRing.setSubText("otimizado");

        scoreTitle.getStyleClass().add("hero-title");
        scoreTitle.setWrapText(true);
        scoreDetail.getStyleClass().add("hero-sub");
        scoreDetail.setWrapText(true);

        ActionButton full = new ActionButton("Otimização Full", "primary");
        full.setOnAction(e -> app.getNavigationManager().navigateTo("full-opt"));
        ActionButton tweaks = new ActionButton("Central de Ajustes", "default");
        tweaks.setOnAction(e -> app.getNavigationManager().navigateTo("tweaks"));
        FlowPane buttons = new FlowPane(10, 10, full, tweaks);

        VBox text = new VBox(8, Ui.sectionLabel("Pontuação de otimização"), scoreTitle, scoreDetail, buttons);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);

        VBox facts = new VBox(10,
                fact("mdi2r-rocket-launch-outline", "Game Booster", boosterValue),
                fact("mdi2t-target", "Counter-Strike 2", cs2Value),
                fact("mdi2t-tune-variant", "Ajustes do NextGen X", tweaksValue));
        facts.getStyleClass().add("hero-facts");
        facts.setMinWidth(230);

        HBox hero = new HBox(26, scoreRing, text, facts);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.getStyleClass().addAll("card", "hero-card");

        // Stack the facts under the text on narrow windows.
        hero.widthProperty().addListener((o, a, w) -> {
            boolean narrow = w.doubleValue() < 820;
            facts.setVisible(!narrow);
            facts.setManaged(!narrow);
        });
        return hero;
    }

    private HBox fact(String icon, String label, Label value) {
        Label l = new Label(label);
        l.getStyleClass().add("fact-label");
        value.getStyleClass().add("fact-value");
        VBox text = new VBox(1, l, value);
        HBox row = new HBox(10, Ui.icon(icon, 18), text);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("fact-row");
        return row;
    }

    private void refreshScore() {
        TweakService service = app.getTweakService();
        Ui.async(() -> {
            List<Tweak> target = service.tweaksFor(Tweak.Profile.GAMER);
            int applicable = 0, applied = 0;
            for (Tweak t : target) {
                TweakService.Status s = service.status(t);
                if (s == TweakService.Status.UNAVAILABLE) continue;
                applicable++;
                if (s == TweakService.Status.APPLIED) applied++;
            }
            return new int[]{applied, applicable, service.appliedByNextGenCount()};
        }, r -> {
            if (r == null) return;
            double ratio = r[1] == 0 ? 0 : r[0] / (double) r[1];
            scoreRing.setProgress(ratio);
            scoreRing.setCenterText(Math.round(ratio * 100) + "%");
            if (ratio >= 0.9) {
                scoreTitle.setText("Seu PC está pronto para jogar.");
                scoreDetail.setText(r[0] + " de " + r[1] + " ajustes do perfil Gamer ativos. Para latência mínima no CS2, aplique o perfil Competitivo.");
            } else {
                scoreTitle.setText((r[1] - r[0]) + " otimizações disponíveis para o seu PC");
                scoreDetail.setText(r[0] + " de " + r[1] + " ajustes do perfil Gamer ativos. A Otimização Full aplica tudo com ponto de restauração e reversão em 1 clique.");
            }
            tweaksValue.setText(r[2] + " aplicados · reversíveis");
        });
        boosterValue.setText(app.getGameBoosterService().isGameActive() ? "Ativo em " + app.getGameBoosterService().activeGame()
                : app.getGameBoosterService().isRunning() ? "Aguardando jogo" : "Desligado");
        Ui.async(() -> app.getSteamLocator().cs2Root() != null, found ->
                cs2Value.setText(Boolean.TRUE.equals(found) ? "Instalado" : "Não encontrado"));
    }

    // ── Gauges ──────────────────────────────────────────────────────

    private Node buildGauges() {
        cpuGauge = new Gauge("", "%", 100, 118);
        gpuGauge = new Gauge("", "%", 100, 118);
        ramGauge = new Gauge("", "%", 100, 118);
        cpuSub = new Label("--");
        gpuSub = new Label("--");
        ramSub = new Label("--");

        pingValue = new Label("--");
        cpuTemp = new Label("--");
        gpuTemp = new Label("--");
        diskValue = new Label("--");
        VBox vitals = Ui.card("vitals-card");
        vitals.getChildren().addAll(Ui.cardHeader("mdi2h-heart-pulse", "Sinais vitais", null),
                vital("Ping", pingValue), vital("Temp. CPU", cpuTemp), vital("Temp. GPU", gpuTemp), vital("Disco em uso", diskValue));

        return new ResponsiveGrid(230, 4,
                gaugeCard("mdi2c-chip", "Processador", cpuGauge, cpuSub),
                gaugeCard("mdi2e-expansion-card-variant", "Placa de vídeo", gpuGauge, gpuSub),
                gaugeCard("mdi2m-memory", "Memória", ramGauge, ramSub),
                vitals);
    }

    private VBox gaugeCard(String icon, String title, Gauge gauge, Label sub) {
        sub.getStyleClass().add("gauge-sub");
        sub.setWrapText(true);
        VBox card = Ui.card("gauge-card");
        card.setAlignment(Pos.TOP_CENTER);
        HBox header = Ui.cardHeader(icon, title, null);
        card.getChildren().addAll(header, gauge, sub);
        return card;
    }

    private HBox vital(String label, Label value) {
        Label l = new Label(label);
        l.getStyleClass().add("vital-label");
        value.getStyleClass().add("vital-value");
        HBox row = new HBox(l, Ui.spacer(), value);
        row.getStyleClass().add("vital-row");
        return row;
    }

    private void update(SystemSnapshot s) {
        cpuGauge.setValue(s.getCpuUsage());
        gpuGauge.setValue(s.getGpuUsage());
        ramGauge.setValue(s.getRamUsagePercent());
        cpuSub.setText(s.getCpuFrequency() > 0 ? String.format(Locale.US, "%.2f GHz · %d threads", s.getCpuFrequency() / 1000.0, s.getCpuThreads()) : "--");
        gpuSub.setText(s.getGpuVramTotal() > 0 ? String.format(Locale.US, "VRAM %.1f / %.1f GB", s.getGpuVramUsed() / 1024.0, s.getGpuVramTotal() / 1024.0) : shortName(s.getGpuName()));
        ramSub.setText(String.format(Locale.US, "%.1f / %.1f GB", s.getRamUsed() / 1073741824.0, s.getRamTotal() / 1073741824.0));
        pingValue.setText(s.getNetworkPing() > 0 ? String.format(Locale.US, "%.0f ms", s.getNetworkPing()) : "--");
        cpuTemp.setText(s.getCpuTemperature() > 0 ? String.format(Locale.US, "%.0f °C", s.getCpuTemperature()) : "sem sensor");
        gpuTemp.setText(s.getGpuTemperature() > 0 ? String.format(Locale.US, "%.0f °C", s.getGpuTemperature()) : "--");
        diskValue.setText(s.getDiskUsagePercent() > 0 ? String.format(Locale.US, "%.0f%%", s.getDiskUsagePercent()) : "--");
        if (s.getCpuModel() != null && !s.getCpuModel().isBlank()) specCpu.setText(s.getCpuModel().trim());
        if (s.getGpuName() != null && !s.getGpuName().isBlank()) specGpu.setText(s.getGpuName().trim());
        if (s.getRamTotal() > 0) specRam.setText(String.format(Locale.US, "%.0f GB", s.getRamTotal() / 1073741824.0));
    }

    // ── Quick actions ───────────────────────────────────────────────

    private Node buildQuickActions() {
        return new ResponsiveGrid(240, 4,
                quickAction("mdi2m-memory", "Liberar RAM", "Esvazia o cache Standby e a lista modificada.", "Liberar", btn ->
                        Ui.run(btn, () -> app.getMemoryService().clean(MemoryService.CleanMode.DEEP), r -> {
                            if (r == null) return;
                            NotificationManager.show(r.success() ? "RAM: " + Ui.formatBytes(r.freedBytes()) + " movidos para memória livre." : r.message(),
                                    r.success() ? NotificationManager.Type.SUCCESS : NotificationManager.Type.WARNING);
                        })),
                quickAction("mdi2b-broom", "Limpeza rápida", "Temporários, relatórios de erro e caches seguros.", "Limpar", btn ->
                        Ui.run(btn, () -> {
                            long total = 0;
                            for (CleanupService.Target t : app.getCleanupService().targets()) {
                                if (t.recommended()) total += app.getCleanupService().clean(t);
                            }
                            return total;
                        }, freed -> {
                            if (freed != null) NotificationManager.success("Limpeza concluída: " + Ui.formatBytes(freed) + " liberados.");
                        })),
                quickAction("mdi2d-dns-outline", "Renovar DNS", "Limpa o cache DNS para corrigir sites e servidores lentos.", "Executar", btn ->
                        Ui.run(btn, () -> app.getNetworkService().flushDns(), out -> NotificationManager.success("Cache DNS limpo."))),
                quickAction("mdi2r-rocket-launch-outline", "Game Booster", "Otimiza automaticamente ao abrir o CS2.", "Configurar", btn ->
                        app.getNavigationManager().navigateTo("cs2")));
    }

    private VBox quickAction(String icon, String title, String desc, String action, java.util.function.Consumer<ActionButton> onClick) {
        ActionButton btn = new ActionButton(action, "default");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> onClick.accept(btn));
        Region push = new Region();
        VBox.setVgrow(push, Priority.ALWAYS);
        VBox card = Ui.card("quick-card");
        card.getChildren().addAll(Ui.cardHeader(icon, title, null), Ui.muted(desc), push, btn);
        return card;
    }

    // ── System summary ─────────────────────────────────────────────

    private Node buildSystemCard() {
        VBox card = Ui.card();
        card.getChildren().add(Ui.cardHeader("mdi2d-desktop-tower-monitor", "Seu sistema", null));
        ResponsiveGrid grid = new ResponsiveGrid(220, 4,
                spec("Processador", specCpu),
                spec("Placa de vídeo", specGpu),
                spec("Memória", specRam),
                spec("Windows", new Label(System.getProperty("os.name") + " (" + System.getProperty("os.version") + ")")));
        grid.gaps(12, 12);
        card.getChildren().add(grid);
        return card;
    }

    private VBox spec(String label, Label v) {
        Label l = new Label(label);
        l.getStyleClass().add("fact-label");
        v.getStyleClass().add("spec-value");
        v.setWrapText(true);
        VBox box = new VBox(3, l, v);
        box.getStyleClass().add("spec-tile");
        return box;
    }

    private static String shortName(String gpu) {
        return gpu == null || gpu.isBlank() ? "--" : gpu.replace("NVIDIA GeForce ", "").replace("AMD Radeon ", "Radeon ");
    }
}
