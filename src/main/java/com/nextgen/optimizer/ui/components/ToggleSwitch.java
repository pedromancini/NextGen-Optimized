package com.nextgen.optimizer.ui.components;

import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * A custom toggle switch control with smooth animation.
 * Features a sliding thumb on a track that changes colour based on state.
 */
public class ToggleSwitch extends HBox {

    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final StringProperty text = new SimpleStringProperty("");

    private final Region track;
    private final Region thumb;
    private final Label label;

    private EventHandler<ActionEvent> onAction;

    private static final double THUMB_OFF_X = 3.0;
    private static final double THUMB_ON_X = 23.0;
    private static final Duration ANIM_DURATION = Duration.millis(150);

    public ToggleSwitch() {
        this(false);
    }

    public ToggleSwitch(boolean initialState) {
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(8);
        getStyleClass().add("toggle-switch");

        // --- Track ---
        track = new Region();
        track.getStyleClass().add("toggle-switch-track");
        track.setMinSize(44, 22);
        track.setMaxSize(44, 22);
        track.setPrefSize(44, 22);

        // --- Thumb ---
        thumb = new Region();
        thumb.getStyleClass().add("toggle-switch-thumb");
        thumb.setMinSize(16, 16);
        thumb.setMaxSize(16, 16);
        thumb.setPrefSize(16, 16);
        thumb.setTranslateX(initialState ? THUMB_ON_X : THUMB_OFF_X);
        thumb.setTranslateY(0);

        StackPane trackContainer = new StackPane(track, thumb);
        trackContainer.setAlignment(Pos.CENTER_LEFT);
        trackContainer.setMinSize(44, 22);
        trackContainer.setMaxSize(44, 22);
        trackContainer.setPrefSize(44, 22);
        trackContainer.setCursor(javafx.scene.Cursor.HAND);

        // --- Label ---
        label = new Label();
        label.getStyleClass().add("toggle-switch-label");
        label.textProperty().bind(text);
        label.managedProperty().bind(label.textProperty().isNotEmpty());
        label.visibleProperty().bind(label.textProperty().isNotEmpty());

        getChildren().addAll(trackContainer, label);

        // Apply initial state styling
        if (initialState) {
            selected.set(true);
            track.getStyleClass().add("toggle-switch-track-on");
            thumb.getStyleClass().add("toggle-switch-thumb-on");
        }

        // Click handler on the track area
        trackContainer.setOnMouseClicked(e -> toggle());

        // Listen to selected property changes for programmatic updates
        selected.addListener((obs, oldVal, newVal) -> {
            if (newVal != oldVal) {
                animateToggle(newVal);
            }
        });
    }

    /**
     * Toggles the switch state.
     */
    public void toggle() {
        selected.set(!selected.get());
        fireAction();
    }

    private void animateToggle(boolean on) {
        TranslateTransition tt = new TranslateTransition(ANIM_DURATION, thumb);
        tt.setToX(on ? THUMB_ON_X : THUMB_OFF_X);
        tt.setInterpolator(javafx.animation.Interpolator.EASE_BOTH);
        tt.play();

        if (on) {
            if (!track.getStyleClass().contains("toggle-switch-track-on")) {
                track.getStyleClass().add("toggle-switch-track-on");
            }
            if (!thumb.getStyleClass().contains("toggle-switch-thumb-on")) {
                thumb.getStyleClass().add("toggle-switch-thumb-on");
            }
        } else {
            track.getStyleClass().remove("toggle-switch-track-on");
            thumb.getStyleClass().remove("toggle-switch-thumb-on");
        }
    }

    private void fireAction() {
        if (onAction != null) {
            onAction.handle(new ActionEvent(this, null));
        }
    }

    // --- Properties ---

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean value) {
        selected.set(value);
    }

    public StringProperty textProperty() {
        return text;
    }

    public String getText() {
        return text.get();
    }

    public void setText(String value) {
        text.set(value);
    }

    public void setOnAction(EventHandler<ActionEvent> handler) {
        this.onAction = handler;
    }

    public EventHandler<ActionEvent> getOnAction() {
        return onAction;
    }
}
