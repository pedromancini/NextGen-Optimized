package com.nextgen.optimizer.ui.pages;

import com.nextgen.optimizer.App;
import com.nextgen.optimizer.ui.components.*;
import com.nextgen.optimizer.core.NotificationManager;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

/**
 * RAM Page — Memory usage breakdown, standby memory cleaner, and compression info.
 */
public class RamPage extends VBox {

    private final App app;
    private Gauge ramGauge;
    private Label totalLabel, usedLabel, freeLabel;

    public RamPage(App app) {
        this.app = app;
        getStyleClass().add("page-container");
        setSpacing(20);
        setPadding(new Insets(4, 4, 24, 4));
        buildUI();
        bindMonitoring();
    }

    private void buildUI() {
        VBox header = new VBox(4);
        Label title = new Label("📦 Memória RAM");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Limpeza de memória Standby e otimização de cache do sistema");
        sub.getStyleClass().add("page-subtitle");
        header.getChildren().addAll(title, sub);

        // Top Section: Gauge + Cleanup Button
        HBox topSection = new HBox(24);
        topSection.getStyleClass().addAll("card", "card-accent");
        topSection.setAlignment(Pos.CENTER_LEFT);
        topSection.setPadding(new Insets(24));

        ramGauge = new Gauge("USO RAM", "%", 100);

        VBox infoBox = new VBox(12);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        totalLabel = new Label("Total: --- GB");
        totalLabel.getStyleClass().addAll("font-lg", "font-bold", "text-primary");

        usedLabel = new Label("Em Uso: --- GB");
        usedLabel.getStyleClass().addAll("font-md", "text-secondary");

        freeLabel = new Label("Disponível: --- GB");
        freeLabel.getStyleClass().addAll("font-md", "text-secondary");

        ActionButton cleanBtn = new ActionButton("🧹 Limpar Memória em Standby", "primary");
        cleanBtn.setOnAction(e -> {
            new Thread(() -> {
                boolean res = app.getRamService().clearStandbyMemory();
                Platform.runLater(() -> {
                    if (res) NotificationManager.show("Memória Standby limpa com sucesso!", NotificationManager.Type.SUCCESS);
                    else NotificationManager.show("Limpador em execução", NotificationManager.Type.INFO);
                });
            }).start();
        });

        infoBox.getChildren().addAll(totalLabel, usedLabel, freeLabel, cleanBtn);
        topSection.getChildren().addAll(ramGauge, infoBox);

        getChildren().addAll(header, topSection);
    }

    private void bindMonitoring() {
        app.getSystemInfoService().snapshotProperty().addListener((obs, oldVal, snap) -> {
            if (snap == null) return;
            Platform.runLater(() -> {
                ramGauge.setValue(snap.getRamUsagePercent());
                double totalGB = snap.getRamTotal() / (1024.0 * 1024 * 1024);
                double usedGB = snap.getRamUsed() / (1024.0 * 1024 * 1024);
                double freeGB = snap.getRamAvailable() / (1024.0 * 1024 * 1024);
                totalLabel.setText(String.format("Total: %.1f GB", totalGB));
                usedLabel.setText(String.format("Em Uso: %.1f GB", usedGB));
                freeLabel.setText(String.format("Disponível: %.1f GB", freeGB));
            });
        });
    }
}
