package com.nextgen.optimizer.ui.pages;

import com.nextgen.optimizer.App;
import com.nextgen.optimizer.core.NotificationManager;
import com.nextgen.optimizer.model.SystemSnapshot;
import com.nextgen.optimizer.services.ProcessService;
import com.nextgen.optimizer.ui.components.*;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Monitor Page — real-time hardware monitor plus a practical process inspector.
 */
public class MonitorPage extends VBox {

    private static final int PROCESS_LIMIT = 40;

    private final App app;
    private Gauge cpuGauge, gpuGauge, ramGauge;
    private LiveChart liveChart;
    private MetricCard cpuTempCard, gpuTempCard, cpuClockCard, gpuClockCard;
    private MetricCard ramCard, vramCard, netCard, diskCard;
    private VBox processList;
    private Label processSummaryLabel;
    private Label selectedPathLabel;
    private Label selectedCommandLabel;
    private TextField processSearch;
    private ActionButton refreshProcessesButton;
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(350));
    private volatile boolean processRefreshRunning = false;
    private List<ProcessService.ProcessInfo> latestProcesses = new ArrayList<>();

    public MonitorPage(App app) {
        this.app = app;
        getStyleClass().add("page-container");
        setSpacing(20);
        setPadding(new Insets(4, 4, 24, 4));
        buildUI();
        bindMonitoring();
        refreshProcesses();
    }

    private void buildUI() {
        VBox header = new VBox(4);
        Label title = new Label("Monitor em Tempo Real");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Uso de CPU, GPU, RAM, rede, disco e processos que mais estão puxando o sistema");
        sub.getStyleClass().add("page-subtitle");
        header.getChildren().setAll(com.nextgen.optimizer.ui.components.Ui.pageHeader("mdi2c-chart-timeline-variant", "Monitor em Tempo Real", sub.getText()));

        HBox gaugesRow = new HBox(16);
        gaugesRow.setAlignment(Pos.CENTER);

        cpuGauge = new Gauge("CPU", "%", 100);
        gpuGauge = new Gauge("GPU", "%", 100);
        ramGauge = new Gauge("RAM", "%", 100);

        VBox cpuBox = wrapGauge(cpuGauge, "Processador");
        VBox gpuBox = wrapGauge(gpuGauge, "Placa de video");
        VBox ramBox = wrapGauge(ramGauge, "Memoria");

        HBox.setHgrow(cpuBox, Priority.ALWAYS);
        HBox.setHgrow(gpuBox, Priority.ALWAYS);
        HBox.setHgrow(ramBox, Priority.ALWAYS);
        gaugesRow.getChildren().addAll(cpuBox, gpuBox, ramBox);

        GridPane metricsGrid = buildMetricsGrid();
        VBox chartBox = buildChartBox();
        VBox processInspector = buildProcessInspector();

        getChildren().addAll(header, gaugesRow, metricsGrid, chartBox, processInspector);
    }

    private GridPane buildMetricsGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);

        cpuTempCard = new MetricCard("TEMP", "TEMP CPU", "--", "C");
        gpuTempCard = new MetricCard("TEMP", "TEMP GPU", "--", "C");
        cpuClockCard = new MetricCard("CPU", "CLOCK CPU", "--", "MHz");
        gpuClockCard = new MetricCard("GPU", "CLOCK GPU", "--", "MHz");
        ramCard = new MetricCard("RAM", "RAM USADA", "--", "GB");
        vramCard = new MetricCard("VRAM", "VRAM", "--", "GB");
        netCard = new MetricCard("NET", "REDE", "--", "Mbps");
        diskCard = new MetricCard("SSD", "DISCO", "--", "MB/s");

        grid.add(cpuTempCard, 0, 0);
        grid.add(gpuTempCard, 1, 0);
        grid.add(cpuClockCard, 2, 0);
        grid.add(gpuClockCard, 3, 0);
        grid.add(ramCard, 0, 1);
        grid.add(vramCard, 1, 1);
        grid.add(netCard, 2, 1);
        grid.add(diskCard, 3, 1);

        for (int i = 0; i < 4; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25.0);
            grid.getColumnConstraints().add(cc);
        }
        return grid;
    }

    private VBox buildChartBox() {
        VBox chartBox = new VBox(12);
        chartBox.getStyleClass().add("card");
        chartBox.setPadding(new Insets(20));

        HBox chartHeader = new HBox(12);
        chartHeader.setAlignment(Pos.CENTER_LEFT);

        Label chartTitle = new Label("HISTORICO DE CONSUMO");
        chartTitle.getStyleClass().addAll("font-xs", "text-secondary", "font-bold");
        Label chartHint = new Label("CPU / GPU / RAM nos ultimos 60 segundos");
        chartHint.getStyleClass().addAll("font-xs", "text-muted");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        chartHeader.getChildren().addAll(chartTitle, spacer, chartHint);

        liveChart = new LiveChart();
        liveChart.addSeries("CPU (%)", Color.web("#3b82f6"));
        liveChart.addSeries("GPU (%)", Color.web("#8b5cf6"));
        liveChart.addSeries("RAM (%)", Color.web("#06b6d4"));

        chartBox.getChildren().addAll(chartHeader, liveChart);
        return chartBox;
    }

    private VBox buildProcessInspector() {
        VBox section = new VBox(12);
        section.getStyleClass().add("card");
        section.setPadding(new Insets(18));

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label title = new Label("Processos em uso");
        title.getStyleClass().add("card-title");
        Label subtitle = new Label("Busca profunda por PID, nome, caminho e linha de comando; processos sem caminho ou em Temp aparecem marcados.");
        subtitle.getStyleClass().addAll("font-sm", "text-secondary");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        processSearch = new TextField();
        processSearch.setPromptText("Buscar app, PID, caminho...");
        processSearch.getStyleClass().add("process-search-field");
        processSearch.setPrefWidth(280);
        processSearch.textProperty().addListener((obs, oldValue, newValue) -> {
            searchDebounce.setOnFinished(e -> refreshProcesses());
            searchDebounce.playFromStart();
        });

        refreshProcessesButton = new ActionButton("Atualizar", "primary");
        refreshProcessesButton.setOnAction(e -> refreshProcesses());

        header.getChildren().addAll(titleBox, spacer, processSearch, refreshProcessesButton);

        HBox columns = new HBox(10);
        columns.getStyleClass().add("process-header-row");
        columns.getChildren().addAll(
                headerLabel("APP / PID", 230),
                headerLabel("CPU", 70),
                headerLabel("RAM", 80),
                headerLabel("STATUS", 110),
                headerLabel("CAMINHO", 360),
                headerLabel("ACOES", 180)
        );

        processList = new VBox(6);
        ScrollPane scroll = new ScrollPane(processList);
        scroll.getStyleClass().add("content-scroll");
        scroll.setFitToWidth(true);
        scroll.setMinHeight(360);
        scroll.setPrefHeight(420);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        processSummaryLabel = new Label("Carregando processos...");
        processSummaryLabel.getStyleClass().addAll("font-sm", "text-secondary");

        VBox detailsBox = new VBox(6);
        detailsBox.getStyleClass().add("process-detail-box");
        Label detailsTitle = new Label("Detalhes do processo selecionado");
        detailsTitle.getStyleClass().addAll("font-xs", "text-secondary", "font-bold");
        selectedPathLabel = new Label("Caminho: selecione um processo");
        selectedPathLabel.getStyleClass().addAll("font-sm", "text-primary");
        selectedPathLabel.setWrapText(true);
        selectedCommandLabel = new Label("Comando: --");
        selectedCommandLabel.getStyleClass().addAll("font-xs", "text-muted");
        selectedCommandLabel.setWrapText(true);
        detailsBox.getChildren().addAll(detailsTitle, selectedPathLabel, selectedCommandLabel);

        section.getChildren().addAll(header, columns, scroll, processSummaryLabel, detailsBox);
        return section;
    }

    private Label headerLabel(String text, double width) {
        Label label = new Label(text);
        label.getStyleClass().addAll("font-xs", "text-muted", "font-bold");
        label.setMinWidth(width);
        label.setPrefWidth(width);
        return label;
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

    private void refreshProcesses() {
        if (processRefreshRunning || app.getProcessService() == null) return;
        processRefreshRunning = true;
        refreshProcessesButton.setLoading(true);
        String filter = processSearch == null ? "" : processSearch.getText();

        new Thread(() -> {
            List<ProcessService.ProcessInfo> processes =
                    app.getProcessService().getDeepRunningProcesses(filter, PROCESS_LIMIT);
            Platform.runLater(() -> {
                latestProcesses = processes;
                renderProcessRows(processes);
                refreshProcessesButton.setLoading(false);
                processRefreshRunning = false;
            });
        }, "MonitorPage-ProcessRefresh").start();
    }

    private void renderProcessRows(List<ProcessService.ProcessInfo> processes) {
        processList.getChildren().clear();

        if (processes.isEmpty()) {
            Label empty = new Label("Nenhum processo encontrado para essa busca.");
            empty.getStyleClass().addAll("font-sm", "text-secondary");
            processList.getChildren().add(empty);
            processSummaryLabel.setText("0 processos encontrados");
            return;
        }

        long attentionCount = processes.stream()
                .filter(p -> !"OK".equalsIgnoreCase(p.getStatus()) && !"Sistema".equalsIgnoreCase(p.getStatus()))
                .count();

        for (ProcessService.ProcessInfo process : processes) {
            processList.getChildren().add(createProcessRow(process));
        }

        processSummaryLabel.setText(processes.size() + " processos exibidos" +
                (attentionCount > 0 ? " | " + attentionCount + " precisam de atencao" : ""));
    }

    private HBox createProcessRow(ProcessService.ProcessInfo process) {
        HBox row = new HBox(10);
        row.getStyleClass().add("deep-process-row");
        row.setAlignment(Pos.CENTER_LEFT);

        VBox appBox = new VBox(2);
        appBox.setMinWidth(230);
        appBox.setPrefWidth(230);
        Label name = new Label(process.getName().isBlank() ? "(sem nome)" : process.getName());
        name.getStyleClass().addAll("font-sm", "text-primary", "font-bold");
        Label pid = new Label("PID " + process.getPid());
        pid.getStyleClass().addAll("font-xs", "text-muted");
        appBox.getChildren().addAll(name, pid);

        Label cpu = valueLabel(String.format(Locale.US, "%.1f%%", process.getCpuPercent()), 70);
        Label ram = valueLabel(process.getRamMb() + " MB", 80);

        Label status = valueLabel(process.getStatus(), 110);
        status.getStyleClass().add(statusStyle(process.getStatus()));

        Label path = valueLabel(shortenPath(process.getPath()), 360);
        path.getStyleClass().add("process-path-label");

        HBox actions = new HBox(8);
        actions.setMinWidth(180);
        actions.setPrefWidth(180);

        ActionButton locationBtn = new ActionButton("Pasta", "default");
        locationBtn.setDisable(process.getPath().isBlank());
        locationBtn.setOnAction(e -> {
            boolean opened = app.getProcessService().openProcessLocation(process.getPath());
            NotificationManager.show(opened ? "Local do processo aberto" : "Nao foi possivel abrir o local",
                    opened ? NotificationManager.Type.INFO : NotificationManager.Type.WARNING);
        });

        ActionButton killBtn = new ActionButton("Encerrar", "danger");
        killBtn.setDisable(process.getPid() <= 4 || "Sistema".equalsIgnoreCase(process.getStatus()));
        killBtn.setOnAction(e -> terminateProcess(process, killBtn));

        actions.getChildren().addAll(locationBtn, killBtn);

        row.setOnMouseClicked(e -> showProcessDetails(process));
        row.getChildren().addAll(appBox, cpu, ram, status, path, actions);
        return row;
    }

    private Label valueLabel(String text, double width) {
        Label label = new Label(text == null || text.isBlank() ? "--" : text);
        label.getStyleClass().addAll("font-sm", "text-secondary");
        label.setMinWidth(width);
        label.setPrefWidth(width);
        return label;
    }

    private String statusStyle(String status) {
        if ("OK".equalsIgnoreCase(status)) return "text-success";
        if ("Sistema".equalsIgnoreCase(status)) return "text-secondary";
        if (status != null && status.toLowerCase(Locale.ROOT).contains("aten")) return "text-danger";
        return "text-warning";
    }

    private void showProcessDetails(ProcessService.ProcessInfo process) {
        String path = process.getPath().isBlank() ? "nao disponivel ou protegido pelo sistema" : process.getPath();
        String command = process.getCommandLine().isBlank() ? "--" : process.getCommandLine();
        selectedPathLabel.setText("Caminho: " + path);
        selectedCommandLabel.setText("Comando: " + command);

        ClipboardContent content = new ClipboardContent();
        content.putString(path);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void terminateProcess(ProcessService.ProcessInfo process, ActionButton button) {
        button.setLoading(true);
        new Thread(() -> {
            boolean success = app.getProcessService().terminateProcess(process.getPid());
            Platform.runLater(() -> {
                button.setLoading(false);
                NotificationManager.show(success ? process.getName() + " encerrado" : "Falha ao encerrar " + process.getName(),
                        success ? NotificationManager.Type.SUCCESS : NotificationManager.Type.ERROR);
                refreshProcesses();
            });
        }, "MonitorPage-TerminateProcess").start();
    }

    private void bindMonitoring() {
        if (app.getSystemInfoService() == null) return;

        app.getSystemInfoService().snapshotProperty().addListener((obs, oldVal, snap) -> {
            if (snap == null) return;
            Platform.runLater(() -> updateHardwareSnapshot(snap));
        });
    }

    private void updateHardwareSnapshot(SystemSnapshot snap) {
        cpuGauge.setValue(snap.getCpuUsage());
        gpuGauge.setValue(snap.getGpuUsage());
        ramGauge.setValue(snap.getRamUsagePercent());

        liveChart.addDataPoint("CPU (%)", snap.getCpuUsage());
        liveChart.addDataPoint("GPU (%)", snap.getGpuUsage());
        liveChart.addDataPoint("RAM (%)", snap.getRamUsagePercent());

        cpuTempCard.setValue(snap.getCpuTemperature() > 0 ? String.format(Locale.US, "%.0f", snap.getCpuTemperature()) : "--");
        gpuTempCard.setValue(snap.getGpuTemperature() > 0 ? String.format(Locale.US, "%.0f", snap.getGpuTemperature()) : "--");
        cpuClockCard.setValue(snap.getCpuFrequency() > 0 ? String.valueOf(snap.getCpuFrequency()) : "--");
        gpuClockCard.setValue(snap.getGpuCoreClock() > 0 ? String.valueOf(snap.getGpuCoreClock()) : "--");

        ramCard.setValue(formatGb(snap.getRamUsed()) + " / " + formatGb(snap.getRamTotal()));
        vramCard.setValue(snap.getGpuVramTotal() > 0
                ? String.format(Locale.US, "%.1f / %.1f", snap.getGpuVramUsed() / 1024.0, snap.getGpuVramTotal() / 1024.0)
                : "--");
        netCard.setValue(String.format(Locale.US, "%.1f ↓ %.1f ↑", snap.getNetworkDownload(), snap.getNetworkUpload()));
        diskCard.setValue(String.format(Locale.US, "%.1f R / %.1f W", snap.getDiskReadSpeed(), snap.getDiskWriteSpeed()));
    }

    private String formatGb(long bytes) {
        if (bytes <= 0) return "--";
        return String.format(Locale.US, "%.1f", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private String shortenPath(String path) {
        if (path == null || path.isBlank()) return "sem caminho / protegido";
        if (path.length() <= 58) return path;
        return "..." + path.substring(path.length() - 55);
    }
}
