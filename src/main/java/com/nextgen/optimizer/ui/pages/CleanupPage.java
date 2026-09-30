package com.nextgen.optimizer.ui.pages;

import com.nextgen.optimizer.App;
import com.nextgen.optimizer.core.NotificationManager;
import com.nextgen.optimizer.services.CleanupService;
import com.nextgen.optimizer.ui.components.*;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Scan-then-clean: shows how much each cache takes before deleting anything,
 * and only touches what the user selected.
 */
public class CleanupPage extends VBox {

    private final App app;
    private final Map<CleanupService.Target, CheckBox> checks = new LinkedHashMap<>();
    private final Map<CleanupService.Target, Label> sizes = new LinkedHashMap<>();
    private final Map<CleanupService.Target, Long> scanned = new LinkedHashMap<>();
    private final Label totalLabel = new Label("--");
    private final Label totalSub = new Label("Clique em Analisar para medir o espaço recuperável.");
    private final ActionButton scanButton = new ActionButton("Analisar", "default");
    private final ActionButton cleanButton = new ActionButton("Limpar selecionados", "primary");

    public CleanupPage(App app) {
        this.app = app;
        getStyleClass().add("page-container");
        setSpacing(18);

        getChildren().addAll(
                Ui.pageHeader("mdi2b-broom", "Limpeza",
                        "Remove apenas caches e temporários. Arquivos em uso ou modificados recentemente são ignorados."),
                buildSummary(),
                buildList());
        scan();
    }

    private VBox buildSummary() {
        totalLabel.getStyleClass().add("big-number");
        totalSub.getStyleClass().add("text-muted-sm");
        totalSub.setWrapText(true);
        VBox text = new VBox(2, Ui.sectionLabel("Espaço recuperável selecionado"), totalLabel, totalSub);
        HBox.setHgrow(text, Priority.ALWAYS);

        scanButton.setOnAction(e -> scan());
        cleanButton.setOnAction(e -> clean());
        HBox buttons = new HBox(10, scanButton, cleanButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        HBox row = new HBox(16, text, buttons);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox card = Ui.card("hero-card");
        card.getChildren().add(row);
        return card;
    }

    private VBox buildList() {
        VBox card = Ui.card();
        card.getChildren().add(Ui.cardHeader("mdi2f-folder-remove-outline", "O que limpar", null));
        VBox list = new VBox(0);
        list.getStyleClass().add("tweak-list");
        for (CleanupService.Target t : app.getCleanupService().targets()) {
            CheckBox check = new CheckBox();
            check.setSelected(t.recommended());
            check.selectedProperty().addListener((o, a, b) -> updateTotal());
            Label title = new Label(t.title());
            title.getStyleClass().add("tweak-title");
            Label desc = Ui.muted(t.description());
            HBox titleRow = new HBox(8, title);
            titleRow.setAlignment(Pos.CENTER_LEFT);
            if (t.recommended()) titleRow.getChildren().add(Ui.badge("Recomendado", "risk-safe"));
            VBox text = new VBox(3, titleRow, desc);
            text.setMinWidth(0);
            HBox.setHgrow(text, Priority.ALWAYS);
            Label size = new Label("…");
            size.getStyleClass().add("size-label");
            size.setMinWidth(90);
            size.setAlignment(Pos.CENTER_RIGHT);
            HBox row = new HBox(14, check, text, size);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("tweak-row");
            row.setOnMouseClicked(e -> {
                if (e.getTarget() != check) check.setSelected(!check.isSelected());
            });
            checks.put(t, check);
            sizes.put(t, size);
            list.getChildren().add(row);
        }
        card.getChildren().add(list);
        return card;
    }

    private void scan() {
        scanButton.setDisable(true);
        cleanButton.setDisable(true);
        totalSub.setText("Analisando…");
        sizes.values().forEach(l -> l.setText("…"));
        Thread worker = new Thread(() -> {
            for (CleanupService.Target t : checks.keySet()) {
                long bytes = app.getCleanupService().scan(t).bytes();
                Platform.runLater(() -> {
                    scanned.put(t, bytes);
                    sizes.get(t).setText(Ui.formatBytes(bytes));
                    updateTotal();
                });
            }
            Platform.runLater(() -> {
                scanButton.setDisable(false);
                cleanButton.setDisable(!app.isElevatedProcess());
                totalSub.setText("Análise concluída. Desmarque o que quiser manter.");
            });
        }, "CleanupScan");
        worker.setDaemon(true);
        worker.start();
    }

    private void updateTotal() {
        long total = 0;
        for (var e : checks.entrySet()) {
            if (e.getValue().isSelected()) total += scanned.getOrDefault(e.getKey(), 0L);
        }
        totalLabel.setText(Ui.formatBytes(total));
    }

    private void clean() {
        scanButton.setDisable(true);
        cleanButton.setDisable(true);
        cleanButton.setLoading(true);
        Thread worker = new Thread(() -> {
            long total = 0;
            for (var e : checks.entrySet()) {
                if (!e.getValue().isSelected()) continue;
                CleanupService.Target t = e.getKey();
                Platform.runLater(() -> sizes.get(t).setText("limpando…"));
                long freed = app.getCleanupService().clean(t);
                total += freed;
                Platform.runLater(() -> sizes.get(t).setText("✓ " + Ui.formatBytes(freed)));
            }
            long finalTotal = total;
            Platform.runLater(() -> {
                cleanButton.setLoading(false);
                scanButton.setDisable(false);
                cleanButton.setDisable(false);
                totalSub.setText("Limpeza concluída: " + Ui.formatBytes(finalTotal) + " liberados. Itens em uso foram mantidos.");
                NotificationManager.success("Limpeza concluída: " + Ui.formatBytes(finalTotal) + " liberados.");
                scanned.clear();
                updateTotal();
            });
        }, "Cleanup");
        worker.setDaemon(true);
        worker.start();
    }
}
