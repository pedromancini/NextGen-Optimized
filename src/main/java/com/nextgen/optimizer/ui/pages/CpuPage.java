package com.nextgen.optimizer.ui.pages;

import com.nextgen.optimizer.App;
import com.nextgen.optimizer.ui.components.*;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

/**
 * CPU Page — Core Parking toggle, CPU Boost control, topology grid, and temperature gauge.
 */
public class CpuPage extends VBox {

    private final App app;
    private Gauge cpuUsageGauge, cpuTempGauge;
    private Label cpuNameLabel, coresLabel, threadsLabel;

    public CpuPage(App app) {
        this.app = app;
        getStyleClass().add("page-container");
        setSpacing(20);
        setPadding(new Insets(4, 4, 24, 4));
        buildUI();
        loadCpuInfo();
    }

    private void buildUI() {
        VBox header = new VBox(4);
        Label title = new Label("🧠 Processador (CPU)");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Ajustes avançados do processador, Core Parking e monitoramento de temperatura");
        sub.getStyleClass().add("page-subtitle");
        header.getChildren().addAll(title, sub);

        // Info Card
        HBox infoCard = new HBox(20);
        infoCard.getStyleClass().addAll("card", "card-accent");
        infoCard.setAlignment(Pos.CENTER_LEFT);
        infoCard.setPadding(new Insets(24));

        Label icon = new Label("🧠");
        icon.setStyle("-fx-font-size: 48px;");

        VBox details = new VBox(6);
        cpuNameLabel = new Label("Carregando processador...");
        cpuNameLabel.getStyleClass().addAll("font-xl", "font-bold", "text-primary");

        HBox badges = new HBox(12);
        coresLabel = new Label("Núcleos: --");
        coresLabel.getStyleClass().addAll("health-good");
        threadsLabel = new Label("Threads: --");
        threadsLabel.getStyleClass().addAll("font-sm", "text-secondary");

        badges.getChildren().addAll(coresLabel, threadsLabel);
        details.getChildren().addAll(cpuNameLabel, badges);
        infoCard.getChildren().addAll(icon, details);

        // Controls Section
        VBox controlsSection = new VBox(12);
        Label sectionTitle = new Label("Configurações do Processador");
        sectionTitle.getStyleClass().add("section-title");

        HBox parkingRow = createOptRow("🛑", "Estacionamento de Núcleos (Core Parking)",
            "Impede que o Windows desative núcleos físicos para economizar energia",
            app.getCpuService().isCoreParkedSettingExposed(),
            enabled -> app.getCpuService().setCoreParking(enabled));

        HBox boostRow = createOptRow("🚀", "Modo Turbo Boost Máximo",
            "Mantém as frequências em nível máximo durante jogos",
            app.getCpuService().isCpuBoostEnabled(),
            enabled -> app.getCpuService().setCpuBoost(enabled));

        controlsSection.getChildren().addAll(sectionTitle, parkingRow, boostRow);

        // Gauges
        HBox gaugesRow = new HBox(16);
        gaugesRow.setAlignment(Pos.CENTER);

        cpuUsageGauge = new Gauge("USO CPU", "%", 100);
        cpuTempGauge = new Gauge("TEMP CPU", "°C", 100);

        VBox usageBox = wrapGauge(cpuUsageGauge, "Utilização Total");
        VBox tempBox = wrapGauge(cpuTempGauge, "Temperatura do Encapsulamento");
        HBox.setHgrow(usageBox, Priority.ALWAYS);
        HBox.setHgrow(tempBox, Priority.ALWAYS);

        gaugesRow.getChildren().addAll(usageBox, tempBox);

        getChildren().addAll(header, infoCard, controlsSection, gaugesRow);
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

    private HBox createOptRow(String icon, String name, String description, boolean isOptimized, ToggleAction action) {
        HBox row = new HBox(12);
        row.getStyleClass().add("opt-row");
        row.setAlignment(Pos.CENTER_LEFT);

        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("opt-row-icon");

        VBox textBox = new VBox(2);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("opt-row-label");

        Label descLabel = new Label(description);
        descLabel.getStyleClass().add("opt-row-desc");
        descLabel.setWrapText(true);

        textBox.getChildren().addAll(nameLabel, descLabel);

        ToggleSwitch toggle = new ToggleSwitch(isOptimized);
        toggle.setOnAction(e -> {
            boolean newState = toggle.isSelected();
            new Thread(() -> action.execute(newState)).start();
        });

        row.getChildren().addAll(iconLabel, textBox, toggle);
        return row;
    }

    private void loadCpuInfo() {
        new Thread(() -> {
            String model = app.getCpuService().getCpuModel();
            int cores = app.getCpuService().getCoreCount();
            int threads = app.getCpuService().getThreadCount();

            Platform.runLater(() -> {
                cpuNameLabel.setText(model);
                coresLabel.setText("Núcleos Físicos: " + cores);
                threadsLabel.setText("Threads Lógicos: " + threads);
            });
        }).start();

        app.getSystemInfoService().snapshotProperty().addListener((obs, oldVal, snap) -> {
            if (snap == null) return;
            Platform.runLater(() -> {
                cpuUsageGauge.setValue(snap.getCpuUsage());
                cpuTempGauge.setValue(snap.getCpuTemperature());
            });
        });
    }

    @FunctionalInterface
    private interface ToggleAction {
        void execute(boolean enabled);
    }
}
