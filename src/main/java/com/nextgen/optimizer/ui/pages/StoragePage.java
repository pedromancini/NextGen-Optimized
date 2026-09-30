package com.nextgen.optimizer.ui.pages;

import com.nextgen.optimizer.App;
import com.nextgen.optimizer.services.StorageService;
import com.nextgen.optimizer.ui.components.*;
import com.nextgen.optimizer.core.NotificationManager;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.*;

import java.util.List;
import java.util.Map;

/**
 * Storage Page — Comprehensive SSD & HDD management, S.M.A.R.T. health diagnostics,
 * TRIM, Defragmentation, CHKDSK Scan, Disk Cleanup, and Read/Write speed Benchmarks.
 */
public class StoragePage extends VBox {

    private final App app;
    private VBox drivesList;

    public StoragePage(App app) {
        this.app = app;
        getStyleClass().add("page-container");
        setSpacing(22);
        setPadding(new Insets(4, 4, 28, 4));
        buildUI();
        loadDrives();
    }

    private void buildUI() {
        VBox header = new VBox(4);
        Label title = new Label("SSD / Armazenamento & HDDs");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Diagnóstico S.M.A.R.T., otimização TRIM de SSDs, defrag, teste de velocidade e verificação de erros");
        sub.getStyleClass().add("page-subtitle");
        header.getChildren().setAll(com.nextgen.optimizer.ui.components.Ui.pageHeader("mdi2h-harddisk", "Armazenamento", sub.getText()));

        drivesList = new VBox(20);

        getChildren().addAll(header, drivesList);
    }

    private void loadDrives() {
        new Thread(() -> {
            List<Map<String, Object>> drives = app.getStorageService().getDrives();
            Platform.runLater(() -> {
                drivesList.getChildren().clear();
                for (Map<String, Object> d : drives) {
                    drivesList.getChildren().add(createDriveCard(d));
                }
            });
        }).start();
    }

    private VBox createDriveCard(Map<String, Object> drive) {
        VBox card = new VBox(16);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(22));

        String letter = (String) drive.get("letter");
        String type = (String) drive.get("type");
        long totalBytes = (Long) drive.get("total");
        long freeBytes = (Long) drive.get("free");
        long totalGB = totalBytes / (1024L * 1024 * 1024);
        long freeGB = freeBytes / (1024L * 1024 * 1024);
        long usedGB = totalGB - freeGB;
        double usedPercent = totalBytes > 0 ? ((double) (totalBytes - freeBytes) / totalBytes) : 0.0;

        // Top Row: Icon + Drive Title + Health Badge
        HBox top = new HBox(14);
        top.setAlignment(Pos.CENTER_LEFT);

        StorageService.DiskInfo disk = (StorageService.DiskInfo) drive.get("info");
        boolean ssd = "SSD".equalsIgnoreCase(type);
        StackPane icon = new StackPane(Ui.icon(ssd ? "mdi2e-expansion-card-variant" : "mdi2h-harddisk", 22));
        icon.getStyleClass().add("page-icon-tile");

        VBox titleBox = new VBox(3);
        Label name = new Label("Unidade " + letter + " (" + type + ")");
        name.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: white;");
        Label space = new Label(usedGB + " GB usados de " + totalGB + " GB (" + freeGB + " GB livres)"
                + (disk != null && !disk.model().isBlank() ? " · " + disk.model() + " · " + disk.busType() : ""));
        space.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8;");
        titleBox.getChildren().addAll(name, space);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        String health = disk == null ? "" : disk.health();
        boolean healthy = "Healthy".equalsIgnoreCase(health);
        Label healthBadge = Ui.badge(health.isBlank() ? "SAÚDE: NÃO INFORMADA" : healthy ? "SAÚDE: BOA" : "SAÚDE: " + health.toUpperCase(),
                health.isBlank() ? "badge-muted" : healthy ? "risk-safe" : "risk-advanced");

        top.getChildren().addAll(icon, titleBox, spacer, healthBadge);

        // Visual Capacity Bar
        VBox barBox = new VBox(4);
        ProgressBar prog = new ProgressBar(usedPercent);
        prog.setMaxWidth(Double.MAX_VALUE);
        prog.setStyle("-fx-accent: #3b82f6; -fx-pref-height: 8px;");
        barBox.getChildren().add(prog);

        // Benchmark Results Area (Dynamic display)
        HBox benchResultsBox = new HBox(16);
        benchResultsBox.setAlignment(Pos.CENTER_LEFT);
        benchResultsBox.setPadding(new Insets(10, 14, 10, 14));
        benchResultsBox.setStyle("-fx-background-color: #0d111d; -fx-border-color: #1e293b; -fx-border-radius: 8; -fx-background-radius: 8;");
        benchResultsBox.setVisible(false);
        benchResultsBox.setManaged(false);

        Label readLbl = new Label("Leitura: -- MB/s");
        readLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #38bdf8;");
        Label writeLbl = new Label("Escrita: -- MB/s");
        writeLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #818cf8;");
        benchResultsBox.getChildren().addAll(new Label("Velocidade sequencial (256 MB, sem cache):"), readLbl, writeLbl);

        // Functions Section Header
        Label toolsHeader = new Label("FERRAMENTAS & OTIMIZAÇÃO DA UNIDADE");
        toolsHeader.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px; -fx-font-weight: bold;");

        // Tool Grid (6 Actions)
        GridPane toolsGrid = new GridPane();
        toolsGrid.setHgap(12);
        toolsGrid.setVgap(12);

        // Column sizing (3 equal columns)
        for (int i = 0; i < 3; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(33.33);
            toolsGrid.getColumnConstraints().add(cc);
        }

        // Action 1: TRIM (SSD Optimization)
        ActionButton trimBtn = new ActionButton("Otimizar (TRIM)", "primary");
        trimBtn.setMaxWidth(Double.MAX_VALUE);
        trimBtn.setOnAction(e -> {
            trimBtn.setDisable(true);
            trimBtn.setText("Executando TRIM...");
            new Thread(() -> {
                boolean ok = app.getStorageService().runTrim(letter);
                Platform.runLater(() -> {
                    trimBtn.setDisable(false);
                    trimBtn.setText("Otimizar (TRIM)");
                    NotificationManager.show(ok ? "TRIM executado na unidade " + letter : "O Windows não executou o TRIM em " + letter + " (requer administrador e SSD).",
                            ok ? NotificationManager.Type.SUCCESS : NotificationManager.Type.WARNING);
                });
            }).start();
        });

        // Action 2: Benchmark Read/Write Speed
        ActionButton benchBtn = new ActionButton("Teste de velocidade", "default");
        benchBtn.setMaxWidth(Double.MAX_VALUE);
        benchBtn.setOnAction(e -> {
            benchBtn.setDisable(true);
            benchBtn.setText("Testando Disco...");
            benchResultsBox.setVisible(true);
            benchResultsBox.setManaged(true);
            readLbl.setText("Leitura: medindo...");
            writeLbl.setText("Escrita: medindo...");
            new Thread(() -> {
                double[] speeds = app.getStorageService().runBenchmark(letter);
                Platform.runLater(() -> {
                    benchBtn.setDisable(false);
                    benchBtn.setText("Teste de velocidade");
                    if (speeds == null) {
                        readLbl.setText("Leitura: falhou");
                        writeLbl.setText("Escrita: falhou");
                        NotificationManager.warning("Não foi possível testar " + letter + " (unidade somente leitura ou sem espaço).");
                        return;
                    }
                    readLbl.setText(String.format("Leitura: %.0f MB/s", speeds[0]));
                    writeLbl.setText(String.format("Escrita: %.0f MB/s", speeds[1]));
                    NotificationManager.show(String.format("Teste de %s: leitura %.0f MB/s · escrita %.0f MB/s", letter, speeds[0], speeds[1]), NotificationManager.Type.SUCCESS);
                });
            }).start();
        });

        // Action 3: CHKDSK Scan Integrity
        ActionButton chkdskBtn = new ActionButton("Verificar erros (CHKDSK)", "default");
        chkdskBtn.setMaxWidth(Double.MAX_VALUE);
        chkdskBtn.setOnAction(e -> {
            chkdskBtn.setDisable(true);
            chkdskBtn.setText("Verificando...");
            new Thread(() -> {
                boolean ok = app.getStorageService().runChkdsk(letter);
                Platform.runLater(() -> {
                    chkdskBtn.setDisable(false);
                    chkdskBtn.setText("Verificar erros (CHKDSK)");
                    NotificationManager.show(ok ? "Nenhum problema encontrado em " + letter
                                    : "O CHKDSK relatou problemas ou não pôde rodar em " + letter + " (requer administrador).",
                            ok ? NotificationManager.Type.SUCCESS : NotificationManager.Type.WARNING);
                });
            }).start();
        });

        // Action 4: Defragmentation
        ActionButton defragBtn = new ActionButton("Desfragmentar (HD)", "default");
        defragBtn.setMaxWidth(Double.MAX_VALUE);
        // SSDs must not be defragmented; Windows already optimizes them with TRIM.
        defragBtn.setDisable(ssd);
        defragBtn.setOnAction(e -> {
            defragBtn.setDisable(true);
            defragBtn.setText("Desfragmentando...");
            new Thread(() -> {
                app.getStorageService().runDefrag(letter);
                Platform.runLater(() -> {
                    defragBtn.setDisable(false);
                    defragBtn.setText("Desfragmentar (HD)");
                    NotificationManager.show("Desfragmentação concluída na unidade " + letter, NotificationManager.Type.SUCCESS);
                });
            }).start();
        });

        // Action 5: Disk Cleanup
        ActionButton cleanBtn = new ActionButton("Abrir Limpeza", "default");
        cleanBtn.setMaxWidth(Double.MAX_VALUE);
        cleanBtn.setOnAction(e -> app.getNavigationManager().navigateTo("cleanup"));

        // Action 6: Detailed SMART Report
        ActionButton smartBtn = new ActionButton("Detalhes S.M.A.R.T.", "default");
        smartBtn.setMaxWidth(Double.MAX_VALUE);
        smartBtn.setOnAction(e -> {
            smartBtn.setDisable(true);
            new Thread(() -> {
                String info = app.getStorageService().getDetailedSmartInfo(letter);
                Platform.runLater(() -> {
                    smartBtn.setDisable(false);
                    NotificationManager.show("S.M.A.R.T.: " + info, NotificationManager.Type.INFO);
                });
            }).start();
        });

        toolsGrid.add(trimBtn, 0, 0);
        toolsGrid.add(benchBtn, 1, 0);
        toolsGrid.add(chkdskBtn, 2, 0);
        toolsGrid.add(defragBtn, 0, 1);
        toolsGrid.add(cleanBtn, 1, 1);
        toolsGrid.add(smartBtn, 2, 1);

        card.getChildren().addAll(top, barBox, benchResultsBox, toolsHeader, toolsGrid);
        return card;
    }
}
