package com.nextgen.optimizer.ui.pages;

import com.nextgen.optimizer.App;
import com.nextgen.optimizer.core.Brand;
import com.nextgen.optimizer.core.NotificationManager;
import com.nextgen.optimizer.services.CleanupService;
import com.nextgen.optimizer.services.MemoryService;
import com.nextgen.optimizer.services.SafetyService;
import com.nextgen.optimizer.tweaks.Tweak;
import com.nextgen.optimizer.tweaks.TweakService;
import com.nextgen.optimizer.ui.components.*;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One-click full optimization: pick a profile, review exactly what changes,
 * then run with a restore point, live progress and a final report.
 */
public class FullOptimizationPage extends VBox {

    private final App app;
    private Tweak.Profile selected = Tweak.Profile.GAMER;
    private final Map<Tweak.Profile, VBox> profileCards = new EnumMap<>(Tweak.Profile.class);
    private final VBox planList = new VBox(6);
    private final Label planSummary = new Label();
    private final ToggleSwitch restorePoint = new ToggleSwitch(true);
    private final ToggleSwitch cleanup = new ToggleSwitch(true);
    private final ToggleSwitch ram = new ToggleSwitch(true);
    private final ToggleSwitch booster = new ToggleSwitch(true);
    private final ActionButton runButton = new ActionButton("Iniciar Otimização Full", "primary");
    private final ActionButton revertButton = new ActionButton("Reverter tudo", "danger");
    private final VBox progressCard = Ui.card("progress-card");
    private final Region progressFill = new Region();
    private final Label progressLabel = new Label();
    private final VBox log = new VBox(6);
    private boolean running;

    public FullOptimizationPage(App app) {
        this.app = app;
        getStyleClass().add("page-container");
        setSpacing(18);

        getChildren().addAll(
                Ui.pageHeader("mdi2r-rocket-launch-outline", "Otimização Full",
                        "Aplica dezenas de ajustes seguros de uma vez — com ponto de restauração e reversão em 1 clique."),
                Ui.sectionLabel("1 · Escolha o perfil"),
                buildProfiles(),
                Ui.sectionLabel("2 · Revise"),
                buildPlanAndOptions(),
                buildRunBar(),
                progressCard);

        progressCard.setVisible(false);
        progressCard.setManaged(false);
        selectProfile(selected);
    }

    // ── Profiles ────────────────────────────────────────────────────

    private Node buildProfiles() {
        ResponsiveGrid grid = new ResponsiveGrid(250, 3);
        for (Tweak.Profile p : Tweak.Profile.values()) {
            VBox card = Ui.card("profile-card");
            String icon = switch (p) {
                case SAFE -> "mdi2s-shield-check-outline";
                case GAMER -> "mdi2g-gamepad-variant-outline";
                case COMPETITIVE -> "mdi2t-target";
            };
            int count = app.getTweakService().tweaksFor(p).size();
            Label title = new Label(p.label);
            title.getStyleClass().add("profile-title");
            Label desc = Ui.muted(p.description);
            FlowPane badges = new FlowPane(6, 6, Ui.badge(count + " ajustes", "badge-impact"));
            if (p == Tweak.Profile.GAMER) badges.getChildren().add(Ui.badge("Recomendado", "badge-profile"));
            if (p == Tweak.Profile.COMPETITIVE) badges.getChildren().add(Ui.badge("Máximo desempenho", "risk-moderate"));
            StackPane chip = new StackPane(Ui.icon(icon, 22));
            chip.getStyleClass().add("profile-icon");
            card.getChildren().addAll(chip, title, desc, badges);
            card.setOnMouseClicked(e -> {
                if (!running) selectProfile(p);
            });
            profileCards.put(p, card);
            grid.getChildren().add(card);
        }
        return grid;
    }

    private void selectProfile(Tweak.Profile p) {
        selected = p;
        profileCards.forEach((k, card) -> {
            card.getStyleClass().remove("profile-card-selected");
            if (k == p) card.getStyleClass().add("profile-card-selected");
        });
        booster.setSelected(p != Tweak.Profile.SAFE);
        refreshPlan();
    }

    // ── Plan + options ──────────────────────────────────────────────

    private Node buildPlanAndOptions() {
        VBox plan = Ui.card();
        planSummary.getStyleClass().add("card-subtitle");
        plan.getChildren().addAll(Ui.cardHeader("mdi2f-format-list-checks", "O que será alterado", null), planSummary, planList);
        ResponsiveGrid.setSpan(plan, 2);

        VBox options = Ui.card();
        options.getChildren().addAll(
                Ui.cardHeader("mdi2c-cog-outline", "Etapas extras", null),
                option(restorePoint, "Criar ponto de restauração", "Rede de segurança do Windows: desfaz tudo, até o que não foi feito pelo " + Brand.NAME + "."),
                option(cleanup, "Limpeza segura de disco", "Temporários, relatórios de erro e caches descartáveis."),
                option(ram, "Liberar RAM ao final", "Esvazia o cache Standby."),
                option(booster, "Ativar Game Booster", "Prioridade alta e plano de energia máximo automaticamente enquanto o CS2 estiver aberto."));
        return new ResponsiveGrid(320, 3, plan, options);
    }

    private HBox option(ToggleSwitch toggle, String title, String desc) {
        Label t = new Label(title);
        t.getStyleClass().add("option-title");
        VBox text = new VBox(2, t, Ui.muted(desc));
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(12, text, toggle);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("option-row");
        return row;
    }

    private void refreshPlan() {
        List<Tweak> tweaks = app.getTweakService().tweaksFor(selected);
        planSummary.setText("Verificando o estado atual de " + tweaks.size() + " ajustes…");
        planList.getChildren().clear();
        Ui.async(() -> tweaks.stream()
                .sorted(java.util.Comparator.comparingInt(t -> t.category().ordinal()))
                .map(t -> Map.entry(t, app.getTweakService().status(t))).toList(), entries -> {
            if (entries == null) return;
            planList.getChildren().clear();
            int pending = 0, active = 0, na = 0;
            Tweak.Category current = null;
            FlowPane chips = null;
            for (var e : entries) {
                if (e.getKey().category() != current) {
                    current = e.getKey().category();
                    Label cat = new Label(current.label);
                    cat.getStyleClass().add("plan-category");
                    chips = new FlowPane(6, 6);
                    planList.getChildren().addAll(cat, chips);
                }
                String style = switch (e.getValue()) {
                    case APPLIED -> { active++; yield "plan-chip-done"; }
                    case UNAVAILABLE -> { na++; yield "plan-chip-na"; }
                    default -> { pending++; yield "plan-chip-pending"; }
                };
                Label chip = new Label(e.getKey().title());
                chip.getStyleClass().addAll("plan-chip", style);
                chips.getChildren().add(chip);
            }
            planSummary.setText(pending + " serão aplicados · " + active + " já ativos · " + na + " não se aplicam a este PC");
        });
    }

    // ── Run ─────────────────────────────────────────────────────────

    private Node buildRunBar() {
        runButton.setOnAction(e -> confirmAndRun());
        revertButton.setOnAction(e -> confirmAndRevert());
        Label hint = Ui.muted("Todos os valores originais são salvos antes de qualquer alteração. Ajustes marcados como \"Avançado\" nunca entram nos perfis.");
        HBox.setHgrow(hint, Priority.ALWAYS);
        HBox bar = new HBox(12, hint, revertButton, runButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("run-bar");
        if (!app.isElevatedProcess()) {
            runButton.setDisable(true);
            revertButton.setDisable(true);
            hint.setText("Abra o " + Brand.NAME + " como administrador para aplicar otimizações.");
        }
        return bar;
    }

    private void confirmAndRun() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Aplicar o perfil " + selected.label + "?\n\n"
                        + (restorePoint.isSelected() ? "Um ponto de restauração do Windows será criado antes.\n" : "")
                        + "Cada ajuste pode ser revertido individualmente na Central de Ajustes ou todos de uma vez aqui.",
                ButtonType.CANCEL, ButtonType.OK);
        alert.setHeaderText("Otimização Full");
        alert.initOwner(getScene().getWindow());
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) run();
    }

    private void run() {
        running = true;
        runButton.setDisable(true);
        revertButton.setDisable(true);
        showProgress("Preparando…");

        boolean doRestore = restorePoint.isSelected();
        boolean doCleanup = cleanup.isSelected();
        boolean doRam = ram.isSelected();
        boolean doBooster = booster.isSelected();
        List<Tweak> tweaks = app.getTweakService().tweaksFor(selected);
        int steps = tweaks.size() + (doRestore ? 1 : 0) + (doCleanup ? 1 : 0) + (doRam ? 1 : 0);

        Thread worker = new Thread(() -> {
            int[] done = {0};
            int[] counts = new int[4]; // applied, already, n/a, failed
            boolean[] restart = {false};
            long freed = 0;

            if (doRestore) {
                step("Criando ponto de restauração do Windows…", null);
                SafetyService.Result r = app.getSafetyService().createRestorePoint(Brand.NAME + " — antes do perfil " + selected.label);
                step(r.message(), r.success() ? "ok" : "warn");
                progress(++done[0], steps);
            }

            for (Tweak t : tweaks) {
                TweakService.Result r;
                try {
                    r = app.getTweakService().apply(t);
                } catch (Exception ex) {
                    r = new TweakService.Result(t, false, false, ex.getMessage());
                }
                if (!r.success()) {
                    counts[3]++;
                    step(t.title() + " — " + r.message(), "error");
                } else if (r.changed()) {
                    counts[0]++;
                    if (t.requiresRestart()) restart[0] = true;
                    step(t.title(), "ok");
                } else if (r.message().startsWith("Não se aplica")) {
                    counts[2]++;
                } else {
                    counts[1]++;
                }
                progress(++done[0], steps);
            }

            if (doCleanup) {
                step("Limpando arquivos temporários…", null);
                for (CleanupService.Target target : app.getCleanupService().targets()) {
                    if (target.recommended()) freed += app.getCleanupService().clean(target);
                }
                step("Limpeza: " + Ui.formatBytes(freed) + " liberados", "ok");
                progress(++done[0], steps);
            }
            if (doRam) {
                MemoryService.CleanResult r = app.getMemoryService().clean(MemoryService.CleanMode.DEEP);
                step(r.success() ? "RAM: " + Ui.formatBytes(r.freedBytes()) + " movidos para memória livre" : r.message(),
                        r.success() ? "ok" : "warn");
                progress(++done[0], steps);
            }

            long freedFinal = freed;
            Platform.runLater(() -> {
                if (doBooster) {
                    app.getSettings().setBoosterEnabled(true);
                    app.getSettings().save();
                    app.applyBoosterSettings();
                }
                finish(counts, restart[0], freedFinal);
            });
        }, "FullOptimization");
        worker.setDaemon(true);
        worker.start();
    }

    private void confirmAndRevert() {
        int count = app.getTweakService().appliedByNextGenCount();
        if (count == 0) {
            NotificationManager.info("Nenhum ajuste do " + Brand.NAME + " está aplicado.");
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Restaurar os valores originais de " + count + " ajustes aplicados pelo " + Brand.NAME + "?",
                ButtonType.CANCEL, ButtonType.OK);
        alert.setHeaderText("Reverter tudo");
        alert.initOwner(getScene().getWindow());
        if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        running = true;
        runButton.setDisable(true);
        revertButton.setDisable(true);
        showProgress("Revertendo…");
        Thread worker = new Thread(() -> {
            int[] done = {0};
            int[] failed = {0};
            app.getTweakService().revertAll(r -> {
                if (!r.success()) failed[0]++;
                step(r.tweak().title() + (r.success() ? " — restaurado" : " — " + r.message()), r.success() ? "ok" : "error");
                progress(++done[0], count);
            });
            Platform.runLater(() -> {
                running = false;
                runButton.setDisable(false);
                revertButton.setDisable(false);
                progressLabel.setText(failed[0] == 0 ? "Tudo restaurado. Reinicie o PC para concluir."
                        : failed[0] + " ajustes não puderam ser restaurados.");
                NotificationManager.show(failed[0] == 0 ? "Configurações originais restauradas." : "Reversão com falhas; veja o registro.",
                        failed[0] == 0 ? NotificationManager.Type.SUCCESS : NotificationManager.Type.WARNING);
                afterChange();
            });
        }, "RevertAll");
        worker.setDaemon(true);
        worker.start();
    }

    private void finish(int[] counts, boolean restart, long freed) {
        running = false;
        runButton.setDisable(false);
        revertButton.setDisable(false);
        progress(1, 1);
        String summary = counts[0] + " aplicados · " + counts[1] + " já estavam ativos · " + counts[2] + " não se aplicam"
                + (counts[3] > 0 ? " · " + counts[3] + " falharam (revertidos)" : "");
        progressLabel.setText((restart ? "Concluído — reinicie o PC para efeito completo. " : "Concluído. ") + summary);
        NotificationManager.show("Otimização Full concluída: " + counts[0] + " ajustes aplicados.",
                counts[3] == 0 ? NotificationManager.Type.SUCCESS : NotificationManager.Type.WARNING);
        afterChange();
    }

    private void afterChange() {
        refreshPlan();
        app.getNavigationManager().invalidate("dashboard", "tweaks", "cs2");
    }

    // ── Progress UI ─────────────────────────────────────────────────

    private void showProgress(String text) {
        progressCard.getChildren().clear();
        log.getChildren().clear();
        progressFill.getStyleClass().setAll("progress-fill");
        progressFill.setPrefWidth(0);
        Pane track = new Pane(progressFill);
        track.getStyleClass().add("progress-track");
        progressFill.prefHeightProperty().bind(track.heightProperty());
        progressLabel.setText(text);
        progressLabel.getStyleClass().setAll("progress-label");
        progressCard.getChildren().addAll(Ui.cardHeader("mdi2p-progress-check", "Progresso", null), track, progressLabel, log);
        progressCard.setVisible(true);
        progressCard.setManaged(true);
    }

    private void progress(int done, int total) {
        Platform.runLater(() -> {
            Pane track = (Pane) progressFill.getParent();
            double ratio = total == 0 ? 1 : Math.min(1, done / (double) total);
            progressFill.setPrefWidth(track.getWidth() * ratio);
            progressFill.resize(track.getWidth() * ratio, track.getHeight());
            if (running) progressLabel.setText(done + " de " + total + " etapas");
        });
    }

    private void step(String text, String state) {
        Platform.runLater(() -> {
            String icon = state == null ? "mdi2d-dots-horizontal" : switch (state) {
                case "ok" -> "mdi2c-check-circle";
                case "warn" -> "mdi2a-alert-circle-outline";
                default -> "mdi2c-close-circle-outline";
            };
            FontIcon fi = Ui.icon(icon, 15);
            fi.getStyleClass().add("log-icon-" + (state == null ? "info" : state));
            Label l = new Label(text, fi);
            l.getStyleClass().add("log-line");
            l.setWrapText(true);
            log.getChildren().add(0, l);
            if (log.getChildren().size() > 80) log.getChildren().remove(80, log.getChildren().size());
        });
    }
}
