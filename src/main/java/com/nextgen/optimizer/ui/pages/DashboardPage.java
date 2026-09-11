package com.nextgen.optimizer.ui.pages;

import com.nextgen.optimizer.App;
import com.nextgen.optimizer.core.NotificationManager;
import com.nextgen.optimizer.model.SystemSnapshot;
import com.nextgen.optimizer.ui.components.ActionButton;
import com.nextgen.optimizer.ui.components.Gauge;
import com.nextgen.optimizer.ui.components.MetricCard;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.Locale;

/**
 * Dashboard — operational home screen with live health, focused actions,
 * and clear next steps instead of decorative noise.
 */
public class DashboardPage extends VBox {

    private final App app;
    private Gauge cpuGauge, gpuGauge, ramGauge;
    private MetricCard pingCard, cpuTempCard, gpuTempCard, diskCard;
    private Label scoreLabel, scoreDescription, healthTitle, healthDetail;
    private Label cpuMini, gpuMini, ramMini, gpuProfileStatus;

    public DashboardPage(App app) {
        this.app = app;
        getStyleClass().add("page-container");
        setSpacing(16);
        setPadding(new Insets(16, 14, 28, 14));
        buildUI();
        bindMonitoring();
        Platform.runLater(this::updateScore);
    }

    private void buildUI() {
        HBox header = buildHeader();
        VBox commandCenter = new VBox(16);
        commandCenter.setAlignment(Pos.TOP_LEFT);

        VBox healthPanel = buildHealthPanel();
        VBox actionPanel = buildActionPanel();
        HBox.setHgrow(healthPanel, Priority.ALWAYS);
        HBox.setHgrow(actionPanel, Priority.ALWAYS);
        commandCenter.getChildren().addAll(healthPanel, actionPanel);

        HBox gauges = buildGaugeRow();
        HBox metrics = buildMetricsRow();
        HBox shortcuts = buildShortcutRow();

        getChildren().addAll(header, gauges, commandCenter, metrics, shortcuts);
    }

    private HBox buildHeader() {
        HBox header = new HBox(14);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("dashboard-header");

        VBox titleBox = new VBox(3);
        Label title = new Label("Painel do Sistema");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Status em tempo real, otimizações e atalhos seguros para jogos");
        subtitle.getStyleClass().add("page-subtitle");
        subtitle.setWrapText(true);
        titleBox.setMinWidth(0);
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label liveBadge = new Label("● Monitorando");
        liveBadge.getStyleClass().add("live-badge");

        header.getChildren().addAll(titleBox, spacer, liveBadge);
        return header;
    }

    private VBox buildHealthPanel() {
        VBox panel = new VBox(14);
        panel.getStyleClass().addAll("card", "dashboard-command-card");
        panel.setPadding(new Insets(20));

        HBox top = new HBox(14);
        top.setAlignment(Pos.CENTER_LEFT);

        VBox scoreBox = new VBox(2);
        scoreLabel = new Label("--");
        scoreLabel.getStyleClass().add("dashboard-score");
        scoreLabel.setMinWidth(110);
        scoreDescription = new Label("Verificando ajustes");
        scoreDescription.setWrapText(true);
        scoreDescription.getStyleClass().addAll("font-sm", "text-secondary");
        scoreBox.getChildren().addAll(scoreLabel, scoreDescription);
        scoreBox.setMinWidth(160);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        VBox healthBox = new VBox(4);
        healthBox.setAlignment(Pos.CENTER_RIGHT);
        healthBox.setMinWidth(0);
        HBox.setHgrow(healthBox, Priority.ALWAYS);
        healthTitle = new Label("Sistema estável");
        healthTitle.getStyleClass().addAll("font-lg", "font-bold", "text-primary");
        healthTitle.setWrapText(true);
        healthDetail = new Label("Aguardando leitura completa do monitor");
        healthDetail.getStyleClass().addAll("font-sm", "text-secondary");
        healthDetail.setWrapText(true);
        healthBox.getChildren().addAll(healthTitle, healthDetail);

        top.getChildren().addAll(scoreBox, spacer, healthBox);

        FlowPane miniStats = new FlowPane(10, 10);
        cpuMini = miniStat("CPU", "--");
        gpuMini = miniStat("GPU", "--");
        ramMini = miniStat("RAM", "--");
        gpuProfileStatus = miniStat("NVIDIA", "Verificando Inspector");
        java.util.concurrent.CompletableFuture.supplyAsync(() -> app.getGpuService().findNvidiaProfileInspector())
            .whenComplete((path, error) -> Platform.runLater(() -> gpuProfileStatus.setText(
                error != null ? "NVIDIA  Consulta indisponível" : path.isBlank() ? "NVIDIA  Inspector não localizado" : "NVIDIA  Inspector pronto")));
        miniStats.getChildren().addAll(cpuMini, gpuMini, ramMini, gpuProfileStatus);

        panel.getChildren().addAll(top, miniStats);
        return panel;
    }

    private Label miniStat(String name, String value) {
        Label label = new Label(name + "  " + value);
        label.getStyleClass().add("mini-stat");
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    private VBox buildActionPanel() {
        VBox panel = new VBox(12);
        panel.getStyleClass().addAll("card", "dashboard-actions-card");
        panel.setPadding(new Insets(20));

        Label title = new Label("Ações principais");
        title.getStyleClass().add("card-title");

        HBox primaryRow = new HBox(10);
        ActionButton gameMode = new ActionButton("Modo Game", "primary");
        gameMode.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(gameMode, Priority.ALWAYS);
        gameMode.setOnAction(e -> runButtonAction(gameMode, () -> {
            app.getPerformanceService().activateGameMode().join();
            Platform.runLater(() -> {
                NotificationManager.show("Modo Game ativado", NotificationManager.Type.SUCCESS);
                updateScore();
            });
        }));

        ActionButton fpsBoost = new ActionButton("FPS Boost", "default");
        fpsBoost.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(fpsBoost, Priority.ALWAYS);
        fpsBoost.setOnAction(e -> runButtonAction(fpsBoost, () -> {
            boolean success = app.getFpsBoostService().applyAll();
            Platform.runLater(() -> {
                NotificationManager.show(success ? "FPS Boost aplicado" : "FPS Boost aplicado parcialmente",
                        success ? NotificationManager.Type.SUCCESS : NotificationManager.Type.WARNING);
                updateScore();
            });
        }));
        primaryRow.getChildren().addAll(gameMode, fpsBoost);

        HBox secondaryRow = new HBox(10);
        ActionButton ram = new ActionButton("Limpar RAM", "default");
        ActionButton dns = new ActionButton("Flush DNS", "default");
        ActionButton temp = new ActionButton("Limpar Temp", "default");
        for (ActionButton b : new ActionButton[]{ram, dns, temp}) {
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
        }
        ram.setOnAction(e -> runButtonAction(ram, () -> {
            boolean ok = app.getRamService().clearStandbyMemory();
            Platform.runLater(() -> NotificationManager.show(ok ? "RAM otimizada" : "RAM parcialmente otimizada",
                    ok ? NotificationManager.Type.SUCCESS : NotificationManager.Type.WARNING));
        }));
        dns.setOnAction(e -> runButtonAction(dns, () -> {
            app.getNetworkService().flushDns();
            Platform.runLater(() -> NotificationManager.show("DNS limpo", NotificationManager.Type.SUCCESS));
        }));
        temp.setOnAction(e -> runButtonAction(temp, () -> {
            long freed = app.getPerformanceService().clearTempFiles();
            Platform.runLater(() -> NotificationManager.show(formatBytes(freed) + " liberados", NotificationManager.Type.SUCCESS));
        }));
        secondaryRow.getChildren().addAll(ram, dns, temp);

        ActionButton balanced = new ActionButton("Otimização recomendada", "success");
        balanced.setMaxWidth(Double.MAX_VALUE);
        balanced.setOnAction(e -> runButtonAction(balanced, () -> {
            app.getPerformanceService().activateGameMode().join();
            boolean fpsOk = app.getFpsBoostService().applyAll();
            app.getRamService().clearStandbyMemory();
            app.getNetworkService().flushDns();
            Platform.runLater(() -> {
                NotificationManager.show(fpsOk ? "Otimização recomendada aplicada" : "Otimização aplicada parcialmente",
                        fpsOk ? NotificationManager.Type.SUCCESS : NotificationManager.Type.WARNING);
                updateScore();
            });
        }));

        panel.getChildren().addAll(title, primaryRow, secondaryRow, balanced);
        return panel;
    }

    private void runButtonAction(ActionButton button, Runnable action) {
        button.setLoading(true);
        new Thread(() -> {
            try {
                action.run();
            } catch (Exception ex) {
                Platform.runLater(() -> NotificationManager.show("Falha na ação: " + ex.getMessage(), NotificationManager.Type.ERROR));
            } finally {
                Platform.runLater(() -> button.setLoading(false));
            }
        }, "Dashboard-Action").start();
    }

    private HBox buildGaugeRow() {
        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER);

        cpuGauge = new Gauge("CPU", "%", 100);
        gpuGauge = new Gauge("GPU", "%", 100);
        ramGauge = new Gauge("RAM", "%", 100);

        VBox cpuBox = wrapGauge(cpuGauge, "Processador");
        VBox gpuBox = wrapGauge(gpuGauge, "Placa de video");
        VBox ramBox = wrapGauge(ramGauge, "Memoria");

        HBox.setHgrow(cpuBox, Priority.ALWAYS);
        HBox.setHgrow(gpuBox, Priority.ALWAYS);
        HBox.setHgrow(ramBox, Priority.ALWAYS);
        row.getChildren().addAll(cpuBox, gpuBox, ramBox);
        return row;
    }

    private VBox wrapGauge(Gauge gauge, String description) {
        VBox box = new VBox(8);
        box.getStyleClass().addAll("card", "compact-gauge-card");
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(18));

        Label desc = new Label(description);
        desc.getStyleClass().addAll("font-sm", "text-secondary");
        box.getChildren().addAll(gauge, desc);
        return box;
    }

    private HBox buildMetricsRow() {
        HBox row = new HBox(12);
        pingCard = new MetricCard("NET", "PING", "--", "ms");
        cpuTempCard = new MetricCard("CPU", "TEMP CPU", "--", "C");
        gpuTempCard = new MetricCard("GPU", "TEMP GPU", "--", "C");
        diskCard = new MetricCard("SSD", "DISCO", "--", "uso");

        HBox.setHgrow(pingCard, Priority.ALWAYS);
        HBox.setHgrow(cpuTempCard, Priority.ALWAYS);
        HBox.setHgrow(gpuTempCard, Priority.ALWAYS);
        HBox.setHgrow(diskCard, Priority.ALWAYS);
        row.getChildren().addAll(pingCard, cpuTempCard, gpuTempCard, diskCard);
        return row;
    }

    private HBox buildShortcutRow() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox gpu = shortcutCard("Perfil NVIDIA", "Abrir GPU", () -> app.getNavigationManager().navigateTo("gpu"));
        VBox monitor = shortcutCard("Processos e uso", "Abrir Monitor", () -> app.getNavigationManager().navigateTo("monitor"));
        VBox network = shortcutCard("Rede e ping", "Abrir Rede", () -> app.getNavigationManager().navigateTo("network"));
        HBox.setHgrow(gpu, Priority.ALWAYS);
        HBox.setHgrow(monitor, Priority.ALWAYS);
        HBox.setHgrow(network, Priority.ALWAYS);
        row.getChildren().addAll(gpu, monitor, network);
        return row;
    }

    private VBox shortcutCard(String title, String action, Runnable onClick) {
        VBox card = new VBox(6);
        card.getStyleClass().add("dashboard-shortcut");
        Label t = new Label(title);
        t.getStyleClass().addAll("font-md", "font-bold", "text-primary");
        Label a = new Label(action);
        a.getStyleClass().addAll("font-sm", "text-accent", "font-bold");
        card.getChildren().addAll(t, a);
        card.setOnMouseClicked(e -> onClick.run());
        return card;
    }

    private void updateScore() {
        if (app.getFpsBoostService() == null) return;

        java.util.concurrent.CompletableFuture.supplyAsync(() -> app.getFpsBoostService().getOptimizationCount())
            .whenComplete((counts, error) -> Platform.runLater(() -> {
            if (error != null) {
                scoreLabel.setText("--");
                scoreDescription.setText("Consulta indisponível");
                return;
            }
            int applied = counts[0];
            int total = counts[1];
            scoreLabel.setText(applied + "/" + total);
            scoreDescription.setText("Ajustes ativos");
        }));
    }

    private void bindMonitoring() {
        if (app.getSystemInfoService() == null) return;

        app.getSystemInfoService().snapshotProperty().addListener((obs, oldVal, snap) -> {
            if (snap == null) return;
            Platform.runLater(() -> updateSnapshot(snap));
        });
    }

    private void updateSnapshot(SystemSnapshot snap) {
        cpuGauge.setValue(snap.getCpuUsage());
        gpuGauge.setValue(snap.getGpuUsage());
        ramGauge.setValue(snap.getRamUsagePercent());

        cpuMini.setText(String.format(Locale.US, "CPU  %.0f%%", snap.getCpuUsage()));
        gpuMini.setText(String.format(Locale.US, "GPU  %.0f%%", snap.getGpuUsage()));
        ramMini.setText(String.format(Locale.US, "RAM  %.0f%%", snap.getRamUsagePercent()));

        pingCard.setValue(snap.getNetworkPing() > 0 ? String.format(Locale.US, "%.0f", snap.getNetworkPing()) : "--");
        cpuTempCard.setValue(snap.getCpuTemperature() > 0 ? String.format(Locale.US, "%.0f", snap.getCpuTemperature()) : "--");
        gpuTempCard.setValue(snap.getGpuTemperature() > 0 ? String.format(Locale.US, "%.0f", snap.getGpuTemperature()) : "--");
        diskCard.setValue(snap.getDiskUsagePercent() > 0 ? String.format(Locale.US, "%.0f%%", snap.getDiskUsagePercent()) : "--");

        updateHealthText(snap);
    }

    private void updateHealthText(SystemSnapshot snap) {
        if (snap.getCpuUsage() >= 90 || snap.getRamUsagePercent() >= 92 || snap.getGpuTemperature() >= 84) {
            healthTitle.setText("Atenção necessária");
            healthTitle.getStyleClass().removeAll("text-success", "text-warning", "text-primary");
            healthTitle.getStyleClass().add("text-warning");
            healthDetail.setText("Uso alto detectado. Abra o Monitor para ver processos pesados antes de otimizar.");
        } else if (snap.getGpuUsage() >= 75 || snap.getCpuUsage() >= 70) {
            healthTitle.setText("Carga de jogo detectada");
            healthTitle.getStyleClass().removeAll("text-success", "text-warning", "text-primary");
            healthTitle.getStyleClass().add("text-accent");
            healthDetail.setText("Sistema em carga. Evite limpar caches enquanto o jogo estiver aberto.");
        } else {
            healthTitle.setText("Sistema estável");
            healthTitle.getStyleClass().removeAll("text-success", "text-warning", "text-primary");
            healthTitle.getStyleClass().add("text-success");
            healthDetail.setText("Tudo dentro do esperado. Use a otimização recomendada antes de jogar.");
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
