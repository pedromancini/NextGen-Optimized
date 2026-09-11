package com.nextgen.optimizer.ui.components;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * A styled action button with variant colouring, loading state, and press animation.
 * Variants: "default", "primary", "success", "danger", "warning".
 */
public class ActionButton extends StackPane {

    private final Label textLabel;
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final String originalText;
    private EventHandler<ActionEvent> onAction;
    private Timeline loadingAnimation;

    /**
     * @param text    Button label
     * @param variant One of: "default", "primary", "success", "danger", "warning"
     */
    public ActionButton(String text, String variant) {
        this.originalText = text;
        getStyleClass().add("action-btn");

        String safeVariant = variant == null || variant.isEmpty() ? "default" : variant;
        if (!"default".equals(safeVariant)) {
            getStyleClass().add("action-btn-" + safeVariant);
        }

        setAlignment(Pos.CENTER);
        setCursor(Cursor.HAND);

        textLabel = new Label(text);
        textLabel.getStyleClass().add("action-btn-text");
        getChildren().add(textLabel);

        // --- Click handler ---
        setOnMouseClicked(e -> {
            if (isDisabled() || loading.get()) return;
            playRipple();
            fireAction();
        });

        // --- Press animation feedback ---
        setOnMousePressed(e -> {
            if (!isDisabled() && !loading.get()) {
                setScaleX(0.97);
                setScaleY(0.97);
            }
        });
        setOnMouseReleased(e -> {
            setScaleX(1.0);
            setScaleY(1.0);
        });

        // --- Loading state ---
        loading.addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                startLoadingAnimation();
                setCursor(Cursor.WAIT);
            } else {
                stopLoadingAnimation();
                setCursor(isDisabled() ? Cursor.DEFAULT : Cursor.HAND);
            }
        });

        // --- Disabled state ---
        disabledProperty().addListener((obs, o, n) -> {
            if (n) {
                setCursor(Cursor.DEFAULT);
                setOpacity(0.5);
            } else {
                setCursor(Cursor.HAND);
                setOpacity(1.0);
            }
        });
    }

    private void playRipple() {
        ScaleTransition st = new ScaleTransition(Duration.millis(100), this);
        st.setFromX(0.97);
        st.setFromY(0.97);
        st.setToX(1.0);
        st.setToY(1.0);
        st.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
        st.play();
    }

    private void fireAction() {
        if (onAction != null) {
            onAction.handle(new ActionEvent(this, null));
        }
    }

    private void startLoadingAnimation() {
        final String[] frames = {".", "..", "...", "..", "."};
        final int[] index = {0};

        loadingAnimation = new Timeline(
                new KeyFrame(Duration.millis(300), e -> {
                    textLabel.setText(originalText + " " + frames[index[0] % frames.length]);
                    index[0]++;
                })
        );
        loadingAnimation.setCycleCount(Timeline.INDEFINITE);
        loadingAnimation.play();
    }

    private void stopLoadingAnimation() {
        if (loadingAnimation != null) {
            loadingAnimation.stop();
            loadingAnimation = null;
        }
        textLabel.setText(originalText);
    }

    // --- Public API ---

    public void setOnAction(EventHandler<ActionEvent> handler) {
        this.onAction = handler;
    }

    public EventHandler<ActionEvent> getOnAction() {
        return onAction;
    }

    public boolean isLoading() {
        return loading.get();
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public void setLoading(boolean value) {
        loading.set(value);
    }

    public void setText(String text) {
        textLabel.setText(text);
    }

    public String getText() {
        return textLabel.getText();
    }
}
