package com.nextgen.optimizer.ui.pages;

import com.nextgen.optimizer.App;
import com.nextgen.optimizer.core.NotificationManager;
import com.nextgen.optimizer.model.AppSettings;
import com.nextgen.optimizer.services.MemoryService;
import com.nextgen.optimizer.services.MemoryService.CleanMode;
import com.nextgen.optimizer.tweaks.Tweak;
import com.nextgen.optimizer.ui.components.*;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.*;
import javafx.util.Duration;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.List;
import java.util.Locale;

/**
 * Memory: live composition (like Task Manager), real cleanups through the
 * Windows memory-list API, the automatic RAM Guard and top consumers.
 */
public class RamPage extends VBox {

    private final App app;
    private final MemoryService memory;
    private final MemoryBar bar = new MemoryBar();
    private final Label usedBig = new Label("--");
    private final Label standbyBig = new Label("--");
    private final Label freeBig = new Label("--");
    private final Label nativeNote = new Label();
    private final VBox topList = new VBox(4);
    private final Label guardStats = new Label();

    public RamPage(App app) {
        this.app = app;
        this.memory = app.getMemoryService();
        getStyleClass().add("page-container");
        setSpacing(18);

        getChildren().addAll(
                Ui.pageHeader("mdi2m-memory", "Memória RAM",
                        "Libere memória de verdade usando a mesma API do RAMMap da Microsoft — sem fechar nenhum programa."),
                buildOverview(),
                Ui.sectionLabel("Liberar memória"),
                buildActions(),
                new ResponsiveGrid(360, 2, buildGuard(), buildTopProcesses()),
                buildRelatedTweaks());

        Timeline refresh = new Timeline(new KeyFrame(Duration.seconds(2), e -> {
            if (getScene() != null) refreshState();
        }));
        refresh.setCycleCount(Timeline.INDEFINITE);
        refresh.play();
        refreshState();
        refreshTopProcesses();
    }

    private Node buildOverview() {
        VBox card = Ui.card("hero-card");
        HBox numbers = new HBox(28,
                bigNumber("Em uso", usedBig, "mem-text-inuse"),
                bigNumber("Standby (cache)", standbyBig, "mem-text-standby"),
                bigNumber("Livre de verdade", freeBig, "mem-text-free"));
        numbers.setAlignment(Pos.CENTER_LEFT);
        nativeNote.getStyleClass().add("text-muted-sm");
        nativeNote.setWrapText(true);
        card.getChildren().addAll(numbers, bar, nativeNote);
        return card;
    }

    private VBox bigNumber(String label, Label value, String style) {
        Label l = new Label(label);
        l.getStyleClass().add("fact-label");
        value.getStyleClass().addAll("big-number", style);
        return new VBox(2, l, value);
    }

    private void refreshState() {
        MemoryService.MemoryState s = memory.readState();
        bar.update(s);
        usedBig.setText(String.format(Locale.US, "%.1f GB · %.0f%%", s.inUse() / 1073741824.0, s.inUsePercent()));
        standbyBig.setText(Ui.formatGb(s.standby()));
        freeBig.setText(Ui.formatGb(s.free()));
        nativeNote.setText(s.detailed()
                ? "Standby é cache de arquivos: o Windows o reaproveita, mas esvaziá-lo antes de jogar evita que o jogo espere a memória ser liberada durante a partida."
                : "Detalhamento indisponível — execute como administrador para ver Standby e liberar memória.");
        guardStats.setText(memory.isGuardRunning()
                ? "Ativo · " + memory.getGuardRuns() + " limpezas automáticas · " + Ui.formatBytes(memory.getGuardFreedTotal()) + " liberados nesta sessão"
                : "Desligado");
    }

    private Node buildActions() {
        return new ResponsiveGrid(260, 3,
                action("mdi2f-feather", "Limpeza leve", "Só o cache de baixa prioridade. Seguro com o jogo aberto.",
                        CleanMode.LIGHT, "default"),
                action("mdi2l-lightning-bolt", "Limpeza profunda", "Esvazia todo o Standby, grava a lista modificada e combina páginas idênticas. Ideal antes de abrir o jogo.",
                        CleanMode.DEEP, "primary"),
                action("mdi2a-arrow-collapse-down", "Reduzir working sets", "Tira da RAM as páginas pouco usadas de todos os programas. Não use com o jogo aberto: causa travadas momentâneas.",
                        CleanMode.WORKING_SETS, "default"));
    }

    private VBox action(String icon, String title, String desc, CleanMode mode, String variant) {
        Label result = new Label();
        result.getStyleClass().add("action-result");
        ActionButton btn = new ActionButton("Executar", variant);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setDisable(!app.isElevatedProcess());
        btn.setOnAction(e -> Ui.run(btn, () -> memory.clean(mode), r -> {
            if (r == null) return;
            result.setText(r.success() ? "✓ " + Ui.formatBytes(r.freedBytes()) + " liberados" : r.message());
            NotificationManager.show(r.success() ? title + ": " + Ui.formatBytes(r.freedBytes()) + " liberados" : r.message(),
                    r.success() ? NotificationManager.Type.SUCCESS : NotificationManager.Type.WARNING);
            refreshState();
        }));
        Region push = new Region();
        VBox.setVgrow(push, Priority.ALWAYS);
        VBox card = Ui.card("quick-card");
        card.getChildren().addAll(Ui.cardHeader(icon, title, null), Ui.muted(desc), push, result, btn);
        return card;
    }

    private Node buildGuard() {
        AppSettings s = app.getSettings();
        ToggleSwitch enabled = new ToggleSwitch(s.isRamGuardEnabled());
        ToggleSwitch onlyGaming = new ToggleSwitch(s.isRamGuardOnlyWhileGaming());
        onlyGaming.setText("Somente com jogo aberto (Game Booster)");
        Slider threshold = new Slider(5, 40, s.getRamGuardThreshold());
        threshold.setMajorTickUnit(5);
        threshold.setMinorTickCount(0);
        threshold.setSnapToTicks(true);
        Label thresholdLabel = new Label();
        thresholdLabel.getStyleClass().add("option-title");
        Runnable label = () -> thresholdLabel.setText("Agir quando a memória livre ficar abaixo de " + (int) threshold.getValue() + "%");
        label.run();

        Runnable save = () -> {
            s.setRamGuardEnabled(enabled.isSelected());
            s.setRamGuardOnlyWhileGaming(onlyGaming.isSelected());
            s.setRamGuardThreshold((int) threshold.getValue());
            s.save();
            app.applyRamGuardSettings();
            refreshState();
        };
        enabled.setOnAction(e -> {
            save.run();
            NotificationManager.show(enabled.isSelected() ? "RAM Guard ativado." : "RAM Guard desativado.", NotificationManager.Type.INFO);
        });
        onlyGaming.setOnAction(e -> save.run());
        threshold.valueProperty().addListener((o, a, b) -> label.run());
        threshold.setOnMouseReleased(e -> save.run());
        enabled.setDisable(!app.isElevatedProcess());

        guardStats.getStyleClass().add("text-muted-sm");
        VBox card = Ui.card();
        card.getChildren().addAll(
                Ui.cardHeader("mdi2s-shield-sync-outline", "RAM Guard", "Libera o cache automaticamente quando a memória livre fica baixa.", enabled),
                thresholdLabel, threshold, onlyGaming, guardStats);
        return card;
    }

    private Node buildTopProcesses() {
        ActionButton refresh = new ActionButton("Atualizar", "default");
        refresh.setOnAction(e -> refreshTopProcesses());
        VBox card = Ui.card();
        card.getChildren().addAll(Ui.cardHeader("mdi2c-chart-bar", "Quem está usando sua RAM", null, refresh), topList);
        return card;
    }

    private void refreshTopProcesses() {
        Ui.async(() -> {
            OperatingSystem os = new SystemInfo().getOperatingSystem();
            return os.getProcesses(OperatingSystem.ProcessFiltering.ALL_PROCESSES, OperatingSystem.ProcessSorting.RSS_DESC, 8);
        }, (List<OSProcess> procs) -> {
            if (procs == null) return;
            topList.getChildren().clear();
            long max = procs.isEmpty() ? 1 : Math.max(1, procs.get(0).getResidentSetSize());
            for (OSProcess p : procs) {
                Label name = new Label(p.getName());
                name.getStyleClass().add("proc-name");
                Label size = new Label(Ui.formatBytes(p.getResidentSetSize()));
                size.getStyleClass().add("proc-size");
                Region fill = new Region();
                fill.getStyleClass().add("proc-bar-fill");
                Pane track = new Pane(fill);
                track.getStyleClass().add("proc-bar");
                double ratio = p.getResidentSetSize() / (double) max;
                track.widthProperty().addListener((o, a, w) -> fill.setPrefWidth(w.doubleValue() * ratio));
                fill.prefHeightProperty().bind(track.heightProperty());
                HBox top = new HBox(name, Ui.spacer(), size);
                topList.getChildren().add(new VBox(3, top, track));
            }
        });
    }

    private Node buildRelatedTweaks() {
        VBox card = Ui.card();
        card.getChildren().add(Ui.cardHeader("mdi2t-tune-variant", "Ajustes que economizam RAM",
                "Impedem que programas fiquem ocupando memória em segundo plano."));
        VBox list = new VBox(0);
        list.getStyleClass().add("tweak-list");
        for (String id : new String[]{"edge-background-off", "chrome-background-off", "widgets-off", "background-apps-off", "sysmain-off"}) {
            Tweak t = app.getTweakService().find(id);
            if (t == null) continue;
            TweakRow row = new TweakRow(t, app.getTweakService(), () -> app.getNavigationManager().invalidate("tweaks", "dashboard"));
            row.setDisable(!app.isElevatedProcess());
            list.getChildren().add(row);
        }
        card.getChildren().add(list);
        return card;
    }
}
