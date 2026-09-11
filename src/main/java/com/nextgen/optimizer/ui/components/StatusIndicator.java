package com.nextgen.optimizer.ui.components;

import javafx.animation.Animation;
import javafx.animation.ScaleTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * A simple LED-style status indicator showing a coloured dot and a label.
 * The ACTIVE state features a subtle pulse animation.
 */
public class StatusIndicator extends HBox {

    /**
     * The possible states for the indicator.
     */
    public enum Status {
        ACTIVE, INACTIVE, WARNING, DANGER
    }

    private final Region dot;
    private final Label label;
    private Status currentStatus;
    private ScaleTransition pulseAnimation;

    /**
     * Creates a status indicator with the given label and initial status.
     */
    public StatusIndicator(String labelText, Status status) {
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(8);
        getStyleClass().add("status-indicator");

        // --- Dot ---
        dot = new Region();
        dot.setMinSize(10, 10);
        dot.setMaxSize(10, 10);
        dot.setPrefSize(10, 10);

        // --- Label ---
        label = new Label(labelText);
        label.getStyleClass().add("status-indicator-label");

        getChildren().addAll(dot, label);

        setStatus(status);
    }

    /**
     * Updates the indicator status, changing the dot style and enabling/disabling the pulse animation.
     */
    public void setStatus(Status status) {
        this.currentStatus = status;

        // Remove all status classes
        dot.getStyleClass().removeAll(
                "status-led-active",
                "status-led-inactive",
                "status-led-warning",
                "status-led-danger"
        );

        // Stop any running pulse
        stopPulse();

        // Apply new status
        switch (status) {
            case ACTIVE -> {
                dot.getStyleClass().add("status-led-active");
                startPulse();
            }
            case INACTIVE -> dot.getStyleClass().add("status-led-inactive");
            case WARNING -> dot.getStyleClass().add("status-led-warning");
            case DANGER -> dot.getStyleClass().add("status-led-danger");
        }
    }

    /**
     * Returns the current status.
     */
    public Status getStatus() {
        return currentStatus;
    }

    /**
     * Updates the label text.
     */
    public void setLabelText(String text) {
        label.setText(text);
    }

    /**
     * Returns the label text.
     */
    public String getLabelText() {
        return label.getText();
    }

    private void startPulse() {
        pulseAnimation = new ScaleTransition(Duration.millis(800), dot);
        pulseAnimation.setFromX(1.0);
        pulseAnimation.setFromY(1.0);
        pulseAnimation.setToX(1.3);
        pulseAnimation.setToY(1.3);
        pulseAnimation.setCycleCount(Animation.INDEFINITE);
        pulseAnimation.setAutoReverse(true);
        pulseAnimation.setInterpolator(javafx.animation.Interpolator.EASE_BOTH);
        pulseAnimation.play();
    }

    private void stopPulse() {
        if (pulseAnimation != null) {
            pulseAnimation.stop();
            pulseAnimation = null;
            dot.setScaleX(1.0);
            dot.setScaleY(1.0);
        }
    }
}
