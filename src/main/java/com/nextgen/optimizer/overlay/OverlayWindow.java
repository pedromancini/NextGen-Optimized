package com.nextgen.optimizer.overlay;

import com.nextgen.optimizer.ui.components.Ui;
import com.nextgen.optimizer.model.SystemSnapshot;
import com.nextgen.optimizer.services.SystemInfoService;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Transparent floating overlay window for showing live FPS/telemetry metrics in games.
 * Dynamically resizes to fit visible metrics without empty bottom space.
 */
public class OverlayWindow {

    public static boolean showFps = true;
    public static boolean show1Low = true;
    public static boolean show01Low = false;
    public static boolean showCpu = true;
    public static boolean showCpuTemp = true;
    public static boolean showGpu = true;
    public static boolean showGpuTemp = true;
    public static boolean showRam = true;
    public static boolean showPing = true;

    private final Stage stage;
    private final SystemInfoService systemInfoService;
    private double xOffset = 0;
    private double yOffset = 0;

    private HBox fpsRow, low1Row, low01Row, cpuRow, gpuRow, ramRow, pingRow;
    private Label fpsVal, low1Val, low01Val, cpuVal, gpuVal, ramVal, pingVal;

    public OverlayWindow(SystemInfoService systemInfoService) {
        this.systemInfoService = systemInfoService;
        this.stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        stage.setTitle("NextGen OSD Window");

        VBox root = buildUI();
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
        scene.getStylesheets().add(getClass().getResource("/css/components.css").toExternalForm());

        stage.setScene(scene);
        stage.setX(40);
        stage.setY(40);

        bindMonitoring();
    }

    private VBox buildUI() {
        VBox root = new VBox(6);
        root.getStyleClass().add("overlay-root");
        root.setPadding(new Insets(10, 14, 10, 14));
        root.setPrefWidth(210);
        root.setMinHeight(Region.USE_PREF_SIZE);
        root.setMaxHeight(Region.USE_PREF_SIZE);

        HBox top = new HBox(6);
        top.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("NEXTGEN X · OSD");
        title.setStyle("-fx-font-weight: 800; -fx-font-size: 11px; -fx-text-fill: #3b82f6;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label close = Ui.glyph("✕");
        close.setStyle("-fx-font-size: 11px; -fx-text-fill: #8b95a8; -fx-cursor: hand;");
        close.setOnMouseClicked(e -> stage.hide());
        top.getChildren().addAll(title, spacer, close);

        top.setOnMousePressed(e -> {
            xOffset = e.getSceneX();
            yOffset = e.getSceneY();
        });
        top.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() - xOffset);
            stage.setY(e.getScreenY() - yOffset);
        });

        root.getChildren().add(top);

        fpsRow = createMetricRow("FPS ATUAL", "144", root);
        if (fpsVal != null) fpsVal.setStyle("-fx-text-fill: #22c55e; -fx-font-weight: 800; -fx-font-size: 13px;");

        low1Row = createMetricRow("1% LOW", "118", root);
        if (low1Val != null) low1Val.setStyle("-fx-text-fill: #86efac; -fx-font-weight: bold; -fx-font-size: 12px;");

        low01Row = createMetricRow("0.1% LOW", "96", root);
        if (low01Val != null) low01Val.setStyle("-fx-text-fill: #facc15; -fx-font-weight: bold; -fx-font-size: 12px;");

        cpuRow = createMetricRow("CPU", "0%", root);
        gpuRow = createMetricRow("GPU", "0%", root);
        ramRow = createMetricRow("RAM", "0%", root);
        pingRow = createMetricRow("PING", "0 ms", root);

        refreshVisibility();

        return root;
    }

    private HBox createMetricRow(String name, String initial, VBox parent) {
        HBox row = new HBox(8);
        row.getStyleClass().add("overlay-metric-row");

        Label label = new Label(name);
        label.getStyleClass().add("overlay-metric-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label val = new Label(initial);
        val.getStyleClass().add("overlay-metric-value");

        row.getChildren().addAll(label, spacer, val);
        parent.getChildren().add(row);

        switch (name) {
            case "FPS ATUAL" -> fpsVal = val;
            case "1% LOW" -> low1Val = val;
            case "0.1% LOW" -> low01Val = val;
            case "CPU" -> cpuVal = val;
            case "GPU" -> gpuVal = val;
            case "RAM" -> ramVal = val;
            case "PING" -> pingVal = val;
        }
        return row;
    }

    public void refreshVisibility() {
        Platform.runLater(() -> {
            setVisibleAndManaged(fpsRow, showFps);
            setVisibleAndManaged(low1Row, show1Low);
            setVisibleAndManaged(low01Row, show01Low);
            setVisibleAndManaged(cpuRow, showCpu);
            setVisibleAndManaged(gpuRow, showGpu);
            setVisibleAndManaged(ramRow, showRam);
            setVisibleAndManaged(pingRow, showPing);

            if (stage.isShowing()) {
                stage.sizeToScene();
            }
        });
    }

    private void setVisibleAndManaged(HBox row, boolean show) {
        if (row != null) {
            row.setVisible(show);
            row.setManaged(show);
        }
    }

    private void bindMonitoring() {
        if (systemInfoService == null) return;
        systemInfoService.snapshotProperty().addListener((obs, oldVal, snap) -> {
            if (snap == null) return;
            Platform.runLater(() -> {
                if (showCpu && cpuVal != null) {
                    if (showCpuTemp) {
                        cpuVal.setText(String.format("%.0f%% | %.0f°C", snap.getCpuUsage(), snap.getCpuTemperature()));
                    } else {
                        cpuVal.setText(String.format("%.0f%%", snap.getCpuUsage()));
                    }
                }
                if (showGpu && gpuVal != null) {
                    if (showGpuTemp) {
                        gpuVal.setText(String.format("%.0f%% | %.0f°C", snap.getGpuUsage(), snap.getGpuTemperature()));
                    } else {
                        gpuVal.setText(String.format("%.0f%%", snap.getGpuUsage()));
                    }
                }
                if (showRam && ramVal != null) {
                    ramVal.setText(String.format("%.0f%%", snap.getRamUsagePercent()));
                }
                if (showPing && pingVal != null) {
                    pingVal.setText(snap.getNetworkPing() > 0 ? String.format("%.0f ms", snap.getNetworkPing()) : "-- ms");
                }
                if (stage.isShowing()) {
                    enforceTopmost();
                }
            });
        });
    }

    private void enforceTopmost() {
        try {
            com.sun.jna.platform.win32.WinDef.HWND hwnd = com.sun.jna.platform.win32.User32.INSTANCE.FindWindow(null, "NextGen OSD Window");
            if (hwnd != null) {
                com.sun.jna.platform.win32.WinDef.HWND HWND_TOPMOST = new com.sun.jna.platform.win32.WinDef.HWND(new com.sun.jna.Pointer(-1));
                int flags = 0x0002 | 0x0001 | 0x0010 | 0x0040;
                com.sun.jna.platform.win32.User32.INSTANCE.SetWindowPos(hwnd, HWND_TOPMOST, 0, 0, 0, 0, flags);
            }
        } catch (Throwable ignored) {}
    }

    public void show() {
        stage.show();
        refreshVisibility();
        enforceTopmost();
    }

    public void hide() {
        stage.hide();
    }

    public boolean isShowing() {
        return stage.isShowing();
    }

    public void toggle() {
        if (stage.isShowing()) {
            stage.hide();
        } else {
            stage.show();
            enforceTopmost();
        }
        refreshVisibility();
    }
}
