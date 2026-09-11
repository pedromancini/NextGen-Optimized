package com.nextgen.optimizer.core;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Singleton toast notification system.
 * <p>
 * Call {@link #init(StackPane)} once with the root overlay pane, then use
 * {@link #show(String, Type)} to display toast notifications.
 * Toasts slide in from the right, stay for 3 seconds, then slide out.
 * Multiple toasts stack vertically.
 */
public final class NotificationManager {

    /**
     * Notification severity types.
     */
    public enum Type {
        SUCCESS, ERROR, WARNING, INFO
    }

    private static VBox toastContainer;
    private static StackPane rootPane;

    private NotificationManager() {
        // Utility class — no instantiation
    }

    /**
     * Initialises the notification system with the root overlay pane.
     * Must be called once at application startup before any {@link #show} calls.
     *
     * @param container The root StackPane of the application (toasts will overlay on top)
     */
    public static void init(StackPane container) {
        rootPane = container;

        toastContainer = new VBox(8);
        toastContainer.setAlignment(Pos.TOP_RIGHT);
        toastContainer.setPadding(new Insets(16, 16, 0, 0));
        toastContainer.setPickOnBounds(false);
        toastContainer.setMouseTransparent(false);
        toastContainer.setMaxWidth(360);
        toastContainer.setMaxHeight(Double.MAX_VALUE);

        // Place the toast container at top-right
        StackPane.setAlignment(toastContainer, Pos.TOP_RIGHT);
        StackPane.setMargin(toastContainer, new Insets(8, 8, 0, 0));

        rootPane.getChildren().add(toastContainer);
    }

    /**
     * Displays a toast notification.
     *
     * @param message The message to display
     * @param type    The notification type (SUCCESS, ERROR, WARNING, INFO)
     */
    public static void show(String message, Type type) {
        if (toastContainer == null) {
            System.err.println("NotificationManager not initialised. Call init() first.");
            return;
        }

        // Build the toast
        Label text = new Label(message);
        text.getStyleClass().add("toast-text");
        text.setWrapText(true);
        text.setMaxWidth(320);

        StackPane toast = new StackPane(text);
        toast.getStyleClass().addAll("toast", getTypeClass(type));
        toast.setPadding(new Insets(12, 20, 12, 20));
        toast.setMaxWidth(340);
        toast.setOpacity(0);
        toast.setTranslateX(360); // Start off-screen to the right

        toastContainer.getChildren().add(toast);

        // --- Slide-in animation ---
        TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), toast);
        slideIn.setFromX(360);
        slideIn.setToX(0);
        slideIn.setInterpolator(javafx.animation.Interpolator.EASE_OUT);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), toast);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1.0);

        ParallelTransition showTransition = new ParallelTransition(slideIn, fadeIn);

        // --- Pause ---
        PauseTransition hold = new PauseTransition(Duration.seconds(3));

        // --- Slide-out animation ---
        TranslateTransition slideOut = new TranslateTransition(Duration.millis(300), toast);
        slideOut.setToX(360);
        slideOut.setInterpolator(javafx.animation.Interpolator.EASE_IN);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), toast);
        fadeOut.setToValue(0);

        ParallelTransition hideTransition = new ParallelTransition(slideOut, fadeOut);
        hideTransition.setOnFinished(e -> toastContainer.getChildren().remove(toast));

        // Chain: show -> hold -> hide
        showTransition.setOnFinished(e -> hold.play());
        hold.setOnFinished(e -> hideTransition.play());
        showTransition.play();

        // Allow clicking the toast to dismiss it early
        toast.setOnMouseClicked(e -> {
            hold.stop();
            hideTransition.play();
        });
    }

    /**
     * Convenience method to show a success notification.
     */
    public static void success(String message) {
        show(message, Type.SUCCESS);
    }

    /**
     * Convenience method to show an error notification.
     */
    public static void error(String message) {
        show(message, Type.ERROR);
    }

    /**
     * Convenience method to show a warning notification.
     */
    public static void warning(String message) {
        show(message, Type.WARNING);
    }

    /**
     * Convenience method to show an info notification.
     */
    public static void info(String message) {
        show(message, Type.INFO);
    }

    private static String getTypeClass(Type type) {
        return switch (type) {
            case SUCCESS -> "toast-success";
            case ERROR -> "toast-error";
            case WARNING -> "toast-warning";
            case INFO -> "toast-info";
        };
    }
}
