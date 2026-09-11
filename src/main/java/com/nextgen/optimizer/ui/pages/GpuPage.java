package com.nextgen.optimizer.ui.pages;

import com.nextgen.optimizer.App;
import com.nextgen.optimizer.core.NotificationManager;
import com.nextgen.optimizer.services.GpuService;
import com.nextgen.optimizer.ui.components.*;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.Map;

/**
 * GPU Page — Hardware details, NVIDIA stats, driver info, and vendor-specific tweaks.
 */
public class GpuPage extends VBox {

    private final App app;
    private Label gpuNameLabel, gpuVendorLabel, gpuDriverLabel, gpuVramLabel;
    private Label nvidiaProfileStatusLabel;
    private Gauge gpuUsageGauge, gpuTempGauge;
    private MetricCard coreClockCard, memClockCard, fanCard, powerCard;

    public GpuPage(App app) {
        this.app = app;
        getStyleClass().add("page-container");
        setSpacing(20);
        setPadding(new Insets(4, 4, 24, 4));
        buildUI();
        loadGpuInfo();
    }

    private void buildUI() {
        VBox header = new VBox(4);
        Label title = new Label("💻 Placa de Vídeo (GPU)");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Estatísticas em tempo real, temperaturas e configurações da sua placa de vídeo");
        sub.getStyleClass().add("page-subtitle");
        header.getChildren().addAll(title, sub);

        // Info Card
        HBox infoCard = new HBox(20);
        infoCard.getStyleClass().addAll("card", "card-accent");
        infoCard.setAlignment(Pos.CENTER_LEFT);
        infoCard.setPadding(new Insets(24));

        Label logo = new Label("🎮");
        logo.setStyle("-fx-font-size: 48px;");

        VBox details = new VBox(6);
        gpuNameLabel = new Label("Carregando placa de vídeo...");
        gpuNameLabel.getStyleClass().addAll("font-xl", "font-bold", "text-primary");

        HBox badges = new HBox(12);
        gpuVendorLabel = new Label("Vendor");
        gpuVendorLabel.getStyleClass().addAll("health-good");
        gpuDriverLabel = new Label("Driver: ---");
        gpuDriverLabel.getStyleClass().addAll("font-sm", "text-secondary");
        gpuVramLabel = new Label("VRAM: ---");
        gpuVramLabel.getStyleClass().addAll("font-sm", "text-secondary");

        badges.getChildren().addAll(gpuVendorLabel, gpuDriverLabel, gpuVramLabel);
        details.getChildren().addAll(gpuNameLabel, badges);
        infoCard.getChildren().addAll(logo, details);

        // Gauges Row
        HBox gaugesRow = new HBox(16);
        gaugesRow.setAlignment(Pos.CENTER);

        gpuUsageGauge = new Gauge("USO GPU", "%", 100);
        gpuTempGauge = new Gauge("TEMP", "°C", 100);

        VBox usageBox = wrapGauge(gpuUsageGauge, "Utilização do Núcleo");
        VBox tempBox = wrapGauge(gpuTempGauge, "Temperatura Atual");
        HBox.setHgrow(usageBox, Priority.ALWAYS);
        HBox.setHgrow(tempBox, Priority.ALWAYS);

        gaugesRow.getChildren().addAll(usageBox, tempBox);

        // Metrics Grid
        HBox metricsRow = new HBox(12);
        coreClockCard = new MetricCard("⚡", "CORE CLOCK", "--", "MHz");
        memClockCard = new MetricCard("💾", "MEMORY CLOCK", "--", "MHz");
        fanCard = new MetricCard("❄️", "VENTOINHA", "--", "%");
        powerCard = new MetricCard("⚡", "CONSUMO", "--", "W");

        HBox.setHgrow(coreClockCard, Priority.ALWAYS);
        HBox.setHgrow(memClockCard, Priority.ALWAYS);
        HBox.setHgrow(fanCard, Priority.ALWAYS);
        HBox.setHgrow(powerCard, Priority.ALWAYS);

        metricsRow.getChildren().addAll(coreClockCard, memClockCard, fanCard, powerCard);

        VBox nvidiaProfileSection = buildNvidiaProfileSection();

        getChildren().addAll(header, infoCard, nvidiaProfileSection, gaugesRow, metricsRow);
    }

    private VBox buildNvidiaProfileSection() {
        VBox section = new VBox(14);
        section.getStyleClass().add("card");
        section.setPadding(new Insets(20));

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(3);
        Label title = new Label("Perfil NVIDIA Low Latency");
        title.getStyleClass().add("card-title");
        Label desc = new Label("Gera um perfil .nip para o NVIDIA Profile Inspector com V-Sync/G-SYNC/AA/limiter desligados e refresh rate no maior disponível.");
        desc.getStyleClass().addAll("font-sm", "text-secondary");
        desc.setWrapText(true);
        titleBox.getChildren().addAll(title, desc);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ActionButton generateBtn = new ActionButton("Gerar .NIP", "default");
        generateBtn.setOnAction(e -> {
            generateBtn.setLoading(true);
            new Thread(() -> {
                GpuService.NvidiaProfileResult result = app.getGpuService().createNvidiaLowLatencyProfile();
                Platform.runLater(() -> {
                    generateBtn.setLoading(false);
                    updateNvidiaProfileStatus(result);
                    NotificationManager.show(result.success() ? "Perfil NVIDIA gerado" : result.message(),
                            result.success() ? NotificationManager.Type.SUCCESS : NotificationManager.Type.ERROR);
                });
            }, "GpuPage-GenerateNvidiaProfile").start();
        });

        ActionButton applyBtn = new ActionButton("Aplicar nas NVIDIA Settings", "primary");
        applyBtn.setOnAction(e -> {
            applyBtn.setLoading(true);
            new Thread(() -> {
                GpuService.NvidiaProfileResult result = app.getGpuService().applyNvidiaLowLatencyProfile();
                Platform.runLater(() -> {
                    applyBtn.setLoading(false);
                    updateNvidiaProfileStatus(result);
                    NotificationManager.show(result.message(),
                            result.success() ? NotificationManager.Type.SUCCESS : NotificationManager.Type.WARNING);
                });
            }, "GpuPage-ApplyNvidiaProfile").start();
        });

        header.getChildren().addAll(titleBox, spacer, generateBtn, applyBtn);

        GridPane settingsGrid = new GridPane();
        settingsGrid.setHgap(10);
        settingsGrid.setVgap(8);
        settingsGrid.add(createNvidiaSettingBadge("V-Sync", "Force off"), 0, 0);
        settingsGrid.add(createNvidiaSettingBadge("G-SYNC", "Force off"), 1, 0);
        settingsGrid.add(createNvidiaSettingBadge("Frame Limiter", "Off"), 2, 0);
        settingsGrid.add(createNvidiaSettingBadge("Pre-rendered", "1"), 3, 0);
        settingsGrid.add(createNvidiaSettingBadge("Refresh rate", "Highest"), 0, 1);
        settingsGrid.add(createNvidiaSettingBadge("Triple Buffering", "Off"), 1, 1);
        settingsGrid.add(createNvidiaSettingBadge("FXAA/MSAA/MFAA", "Off"), 2, 1);
        settingsGrid.add(createNvidiaSettingBadge("Sharpening", "Off"), 3, 1);

        for (int i = 0; i < 4; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25.0);
            settingsGrid.getColumnConstraints().add(cc);
        }

        nvidiaProfileStatusLabel = new Label("Status: aguardando. Coloque nvidiaProfileInspector.exe na pasta tools do app para aplicar automaticamente.");
        nvidiaProfileStatusLabel.getStyleClass().addAll("font-sm", "text-secondary");
        nvidiaProfileStatusLabel.setWrapText(true);

        section.getChildren().addAll(header, settingsGrid, nvidiaProfileStatusLabel);
        return section;
    }

    private VBox createNvidiaSettingBadge(String name, String value) {
        VBox box = new VBox(2);
        box.getStyleClass().add("nvidia-setting-badge");
        Label n = new Label(name);
        n.getStyleClass().addAll("font-xs", "text-muted", "font-bold");
        Label v = new Label(value);
        v.getStyleClass().addAll("font-sm", "text-primary", "font-bold");
        box.getChildren().addAll(n, v);
        return box;
    }

    private void updateNvidiaProfileStatus(GpuService.NvidiaProfileResult result) {
        if (nvidiaProfileStatusLabel == null || result == null) return;
        String tool = result.toolPath() == null || result.toolPath().isBlank()
                ? "Profile Inspector nao encontrado"
                : "Tool: " + result.toolPath();
        String profile = result.profilePath() == null || result.profilePath().isBlank()
                ? ""
                : " | Arquivo: " + result.profilePath();
        nvidiaProfileStatusLabel.setText("Status: " + result.message() + " | " + tool + profile);
        nvidiaProfileStatusLabel.getStyleClass().removeAll("text-success", "text-warning", "text-danger", "text-secondary");
        nvidiaProfileStatusLabel.getStyleClass().add(result.success() ? "text-success" : "text-warning");
    }

    private VBox wrapGauge(Gauge gauge, String description) {
        VBox box = new VBox(8);
        box.getStyleClass().add("card");
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(20));

        Label desc = new Label(description);
        desc.getStyleClass().addAll("font-sm", "text-secondary");

        box.getChildren().addAll(gauge, desc);
        return box;
    }

    private void loadGpuInfo() {
        new Thread(() -> {
            String name = app.getGpuService().getGpuName();
            String vendor = app.getGpuService().detectGpu();
            String driver = app.getGpuService().getGpuDriver();
            long vram = app.getGpuService().getGpuVramTotal();

            Platform.runLater(() -> {
                gpuNameLabel.setText(name);
                gpuVendorLabel.setText(vendor);
                gpuDriverLabel.setText("Driver: " + driver);
                gpuVramLabel.setText("VRAM: " + vram + " MB");
            });

            // Update stats loop
            while (true) {
                Map<String, String> info = app.getGpuService().getNvidiaInfo();
                Platform.runLater(() -> {
                    if (info.containsKey("utilization")) gpuUsageGauge.setValue(parseDouble(info.get("utilization")));
                    if (info.containsKey("temp")) gpuTempGauge.setValue(parseDouble(info.get("temp")));
                    if (info.containsKey("clockCore")) coreClockCard.setValue(info.get("clockCore"));
                    if (info.containsKey("clockMem")) memClockCard.setValue(info.get("clockMem"));
                    if (info.containsKey("fan")) fanCard.setValue(info.get("fan"));
                    if (info.containsKey("power")) powerCard.setValue(info.get("power"));
                });
                try { Thread.sleep(1500); } catch (Exception ignored) { break; }
            }
        }).start();
    }

    private double parseDouble(String val) {
        try {
            return Double.parseDouble(val.replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
