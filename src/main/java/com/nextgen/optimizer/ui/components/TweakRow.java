package com.nextgen.optimizer.ui.components;

import com.nextgen.optimizer.core.NotificationManager;
import com.nextgen.optimizer.tweaks.Tweak;
import com.nextgen.optimizer.tweaks.TweakAction;
import com.nextgen.optimizer.tweaks.TweakService;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * One tweak: title, honest description, risk/impact badges, expandable
 * technical details and a switch that applies or reverts it for real.
 */
public class TweakRow extends VBox {

    private final Tweak tweak;
    private final TweakService service;
    private final ToggleSwitch toggle = new ToggleSwitch(false);
    private final Label state = new Label("Verificando…");
    private final VBox details = new VBox(4);
    private final Runnable onChange;
    private boolean updating;
    private TweakService.Status status;

    public TweakRow(Tweak tweak, TweakService service, Runnable onChange) {
        this.tweak = tweak;
        this.service = service;
        this.onChange = onChange;
        getStyleClass().add("tweak-row");
        setSpacing(8);

        Label title = new Label(tweak.title());
        title.getStyleClass().add("tweak-title");
        title.setWrapText(true);

        Label desc = new Label(tweak.description());
        desc.getStyleClass().add("tweak-desc");
        desc.setWrapText(true);

        FlowPane badges = new FlowPane(6, 6);
        badges.getChildren().add(Ui.badge(tweak.risk().label, tweak.risk().styleClass));
        if (tweak.profile() != null) badges.getChildren().add(Ui.badge(tweak.profile().label, "badge-profile"));
        if (tweak.requiresRestart()) badges.getChildren().add(Ui.badge("Reiniciar", "badge-muted"));
        for (String tag : tweak.impact()) badges.getChildren().add(Ui.badge(tag, "badge-impact"));

        VBox text = new VBox(4, title, desc, badges);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);

        state.getStyleClass().add("tweak-state");
        FontIcon chevron = Ui.icon("mdi2c-chevron-down", 18);
        Label more = new Label("", chevron);
        more.getStyleClass().add("icon-button");
        more.setOnMouseClicked(e -> {
            boolean show = !details.isVisible();
            details.setVisible(show);
            details.setManaged(show);
            chevron.setIconLiteral(show ? "mdi2c-chevron-up" : "mdi2c-chevron-down");
        });

        VBox right = new VBox(6, toggle, state);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.setMinWidth(96);

        HBox top = new HBox(14, text, right, more);
        top.setAlignment(Pos.CENTER_LEFT);

        details.getStyleClass().add("tweak-details");
        if (tweak.warning() != null && tweak.risk() == Tweak.Risk.SAFE) {
            Label warn = new Label("⚠  " + tweak.warning());
            warn.getStyleClass().add("tweak-warning");
            warn.setWrapText(true);
            details.getChildren().add(warn);
        }
        for (TweakAction a : tweak.actions()) {
            Label l = new Label(a.describe());
            l.getStyleClass().add("mono-label");
            l.setWrapText(true);
            details.getChildren().add(l);
        }
        details.setVisible(false);
        details.setManaged(false);

        // Warnings are important enough to show up front for non-trivial tweaks.
        if (tweak.warning() != null && tweak.risk() != Tweak.Risk.SAFE) {
            Label warn = new Label("⚠  " + tweak.warning());
            warn.getStyleClass().add("tweak-warning");
            warn.setWrapText(true);
            text.getChildren().add(warn);
        }

        getChildren().addAll(top, details);

        toggle.setDisable(true);
        toggle.setOnAction(e -> {
            if (!updating) onToggle(toggle.isSelected());
        });
        refresh();
    }

    public Tweak tweak() { return tweak; }

    public TweakService.Status status() { return status; }

    public void refresh() {
        Ui.async(() -> service.status(tweak), s -> {
            if (s != null) show(s);
        });
    }

    private void show(TweakService.Status s) {
        status = s;
        updating = true;
        toggle.setSelected(s == TweakService.Status.APPLIED);
        updating = false;
        getStyleClass().removeAll("tweak-row-on", "tweak-row-na");
        switch (s) {
            case APPLIED -> {
                state.setText(service.canRevert(tweak) ? "Ativo" : "Já ativo");
                getStyleClass().add("tweak-row-on");
                toggle.setDisable(false);
            }
            case PARTIAL -> {
                state.setText("Parcial");
                toggle.setDisable(false);
            }
            case NOT_APPLIED -> {
                state.setText("Padrão");
                toggle.setDisable(false);
            }
            case UNAVAILABLE -> {
                state.setText("N/A neste PC");
                getStyleClass().add("tweak-row-na");
                toggle.setDisable(true);
            }
        }
    }

    private void onToggle(boolean enable) {
        toggle.setDisable(true);
        state.setText(enable ? "Aplicando…" : "Revertendo…");
        Ui.async(() -> enable ? service.apply(tweak) : service.revert(tweak), result -> {
            if (result != null) {
                NotificationManager.show(tweak.title() + ": " + result.message(),
                        result.success() ? NotificationManager.Type.SUCCESS : NotificationManager.Type.WARNING);
            }
            refresh();
            if (onChange != null) onChange.run();
        });
    }
}
