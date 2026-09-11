package com.nextgen.optimizer.ui.pages;

import com.nextgen.optimizer.App;
import com.nextgen.optimizer.overlay.OverlayWindow;
import com.nextgen.optimizer.ui.components.ActionButton;
import com.nextgen.optimizer.ui.components.ToggleSwitch;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

/**
 * Dedicated customization and control page for the floating In-Game Overlay OSD.
 */
public class OverlayPage extends VBox {

    private final App app;
    private Label statusDot;
    private Label statusText;
    private Label offSide;
    private Label onSide;
    private HBox masterCardBox;

    public OverlayPage(App app) {
        this.app = app;
        getStyleClass().add("page-container");
        setSpacing(20);
        setPadding(new Insets(4, 4, 24, 4));
        buildUI();
    }

    private void buildUI() {
        // Header
        VBox header = new VBox(4);
        Label title = new Label("Overlay OSD em Jogo");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Configure quais métricas de FPS, 1% Low e temperaturas aparecem no painel flutuante");
        sub.getStyleClass().add("page-subtitle");
        header.getChildren().addAll(title, sub);

        // Master Switch Pod
        HBox masterCard = buildMasterCard();

        // FPS & Gameplay Telemetry Card
        VBox fpsSection = buildFpsSection();

        // Hardware Metrics Section
        VBox hardwareSection = buildHardwareSection();

        // Network & Clock Section
        VBox miscSection = buildMiscSection();

        getChildren().addAll(header, masterCard, fpsSection, hardwareSection, miscSection);
    }

    private HBox buildMasterCard() {
        masterCardBox = new HBox(24);
        masterCardBox.getStyleClass().add("card");
        masterCardBox.setAlignment(Pos.CENTER_LEFT);
        masterCardBox.setPadding(new Insets(20, 26, 20, 26));

        VBox infoBox = new VBox(6);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        HBox badgeRow = new HBox(8);
        badgeRow.setAlignment(Pos.CENTER_LEFT);
        Label badge = new Label("HUD FLUTUANTE EM JOGOS");
        badge.setStyle("-fx-background-color: #3b82f622; -fx-text-fill: #60a5fa; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3 8; -fx-background-radius: 4;");

        statusDot = new Label("●");
        statusText = new Label("OCULTO");
        badgeRow.getChildren().addAll(badge, statusDot, statusText);

        Label title = new Label("Painel OSD Ativo");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label desc = new Label("Pressione este interruptor ou clique no botão Overlay para alternar a exibição na sua tela.");
        desc.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");
        desc.setWrapText(true);

        infoBox.getChildren().addAll(badgeRow, title, desc);

        // Right side switch
        VBox switchContainer = new VBox(8);
        switchContainer.setAlignment(Pos.CENTER_RIGHT);

        Label switchHeader = new Label("EXIBIR OVERLAY");
        switchHeader.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px; -fx-font-weight: bold;");

        offSide = new Label("OFF");
        offSide.setPrefSize(60, 36);
        offSide.setAlignment(Pos.CENTER);

        onSide = new Label("ON");
        onSide.setPrefSize(60, 36);
        onSide.setAlignment(Pos.CENTER);

        HBox pillSwitch = new HBox(offSide, onSide);
        pillSwitch.setStyle("-fx-background-color: #0f172a; -fx-border-color: #334155; -fx-border-radius: 22; -fx-background-radius: 22; -fx-padding: 3; -fx-cursor: hand;");

        pillSwitch.setOnMouseClicked(e -> {
            app.toggleOverlay();
            updateMasterVisuals();
        });

        switchContainer.getChildren().addAll(switchHeader, pillSwitch);

        updateMasterVisuals();

        masterCardBox.getChildren().addAll(infoBox, switchContainer);
        return masterCardBox;
    }

    private void updateMasterVisuals() {
        boolean active = app.isOverlayShowing();
        if (active) {
            offSide.setStyle("-fx-text-fill: #64748b; -fx-font-weight: bold; -fx-font-size: 13px; -fx-background-color: transparent;");
            onSide.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-background-radius: 18; -fx-effect: dropshadow(three-pass-box, rgba(59,130,246,0.4), 10, 0, 0, 0);");
            statusDot.setStyle("-fx-text-fill: #3b82f6; -fx-font-size: 14px;");
            statusText.setText("VISÍVEL NA TELA");
            statusText.setStyle("-fx-text-fill: #60a5fa; -fx-font-weight: bold; -fx-font-size: 12px;");
            masterCardBox.setStyle("-fx-background-color: linear-gradient(to right, #1e3a8a33, #0f172a); -fx-border-color: #3b82f677; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 20 26 20 26;");
        } else {
            offSide.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-background-radius: 18;");
            onSide.setStyle("-fx-text-fill: #64748b; -fx-font-weight: bold; -fx-font-size: 13px; -fx-background-color: transparent;");
            statusDot.setStyle("-fx-text-fill: #64748b; -fx-font-size: 14px;");
            statusText.setText("OCULTO");
            statusText.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: bold; -fx-font-size: 12px;");
            masterCardBox.setStyle("-fx-background-color: linear-gradient(to right, #111827, #1e293b); -fx-border-color: #3b82f633; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 20 26 20 26;");
        }
    }

    private VBox buildFpsSection() {
        VBox section = new VBox(12);
        section.getStyleClass().add("card");

        Label title = new Label("🎮 Quadros por Segundo & Estabilidade");
        title.getStyleClass().add("card-title");

        section.getChildren().addAll(
                title,
                createOptionRow("FPS Atual (Taxa em tempo real)", OverlayWindow.showFps, val -> OverlayWindow.showFps = val),
                createOptionRow("1% Low FPS (Quedas mínimas de quadros)", OverlayWindow.show1Low, val -> OverlayWindow.show1Low = val),
                createOptionRow("0.1% Low FPS (Micro-stutterings críticos)", OverlayWindow.show01Low, val -> OverlayWindow.show01Low = val)
        );
        return section;
    }

    private VBox buildHardwareSection() {
        VBox section = new VBox(12);
        section.getStyleClass().add("card");

        Label title = new Label("⚡ Monitoramento de Hardware");
        title.getStyleClass().add("card-title");

        section.getChildren().addAll(
                title,
                createOptionRow("Uso da CPU (%)", OverlayWindow.showCpu, val -> OverlayWindow.showCpu = val),
                createOptionRow("Temperatura da CPU (°C)", OverlayWindow.showCpuTemp, val -> OverlayWindow.showCpuTemp = val),
                createOptionRow("Uso da Placa de Vídeo - GPU (%)", OverlayWindow.showGpu, val -> OverlayWindow.showGpu = val),
                createOptionRow("Temperatura da GPU (°C)", OverlayWindow.showGpuTemp, val -> OverlayWindow.showGpuTemp = val),
                createOptionRow("Uso de Memória RAM (%)", OverlayWindow.showRam, val -> OverlayWindow.showRam = val)
        );
        return section;
    }

    private VBox buildMiscSection() {
        VBox section = new VBox(12);
        section.getStyleClass().add("card");

        Label title = new Label("🌐 Conexão & Sistema");
        title.getStyleClass().add("card-title");

        section.getChildren().addAll(
                title,
                createOptionRow("Ping / Latência de Conexão (ms)", OverlayWindow.showPing, val -> OverlayWindow.showPing = val)
        );
        return section;
    }

    private HBox createOptionRow(String labelText, boolean initialValue, BooleanConsumer consumer) {
        HBox row = new HBox(12);
        row.getStyleClass().add("opt-row");
        row.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label(labelText);
        label.getStyleClass().add("opt-row-label");
        HBox.setHgrow(label, Priority.ALWAYS);

        ToggleSwitch toggle = new ToggleSwitch(initialValue);
        toggle.setOnAction(e -> {
            consumer.accept(toggle.isSelected());
            app.refreshOverlayVisibility();
        });

        row.getChildren().addAll(label, toggle);
        return row;
    }

    @FunctionalInterface
    private interface BooleanConsumer {
        void accept(boolean value);
    }
}
