package com.nextgen.optimizer.ui.components;

import com.nextgen.optimizer.core.NotificationManager;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Small factory helpers so every page shares the same building blocks. */
public final class Ui {

    private Ui() {}

    public static FontIcon icon(String literal, int size) {
        FontIcon icon = new FontIcon(literal);
        icon.setIconSize(size);
        icon.getStyleClass().add("ui-icon");
        return icon;
    }

    /**
     * Label for a legacy emoji icon, rendered as a vector icon (color emoji do
     * not render in JavaFX on Windows). Plain text such as "CPU" is kept as-is.
     */
    public static Label glyph(String emoji) {
        String key = emoji == null ? "" : emoji.replace("️", "").trim();
        String literal = switch (key) {
            case "🧠" -> "mdi2c-chip";
            case "🎮" -> "mdi2g-gamepad-variant-outline";
            case "🔥" -> "mdi2f-fire";
            case "🖥", "💻" -> "mdi2m-monitor";
            case "🔌" -> "mdi2p-power-plug-outline";
            case "🛑" -> "mdi2p-pause-octagon-outline";
            case "🚀" -> "mdi2r-rocket-launch-outline";
            case "⚡" -> "mdi2l-lightning-bolt";
            case "💾" -> "mdi2m-memory";
            case "❄" -> "mdi2f-fan";
            case "🔄" -> "mdi2r-refresh";
            case "🔁" -> "mdi2r-repeat";
            case "🔧" -> "mdi2w-wrench-outline";
            case "📡" -> "mdi2a-access-point-network";
            case "☁" -> "mdi2c-cloud-outline";
            case "🔍" -> "mdi2m-magnify";
            case "🛡" -> "mdi2s-shield-outline";
            case "🌐" -> "mdi2w-web";
            case "💿" -> "mdi2h-harddisk";
            default -> null;
        };
        if (literal == null) return new Label(emoji);
        Label l = new Label("", icon(literal, 22));
        l.getStyleClass().add("glyph");
        return l;
    }

    /** Page header: gradient icon tile, title, subtitle and optional trailing actions. */
    public static HBox pageHeader(String iconLiteral, String title, String subtitle, Node... actions) {
        StackPane tile = new StackPane(icon(iconLiteral, 22));
        tile.getStyleClass().add("page-icon-tile");

        Label t = new Label(title);
        t.getStyleClass().add("page-title");
        Label s = new Label(subtitle);
        s.getStyleClass().add("page-subtitle");
        s.setWrapText(true);
        VBox text = new VBox(2, t, s);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox header = new HBox(14, tile, text);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("page-header");
        if (actions.length > 0) {
            HBox right = new HBox(8, actions);
            right.setAlignment(Pos.CENTER_RIGHT);
            right.setMinWidth(Region.USE_PREF_SIZE);
            header.getChildren().add(right);
        }
        return header;
    }

    public static VBox card(String... extraClasses) {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.getStyleClass().addAll(extraClasses);
        return card;
    }

    /** Card title row: icon + title (+ optional subtitle) + trailing nodes. */
    public static HBox cardHeader(String iconLiteral, String title, String subtitle, Node... trailing) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        if (iconLiteral != null) {
            StackPane chip = new StackPane(icon(iconLiteral, 16));
            chip.getStyleClass().add("card-icon-chip");
            row.getChildren().add(chip);
        }
        Label t = new Label(title);
        t.getStyleClass().add("card-title");
        VBox text = new VBox(1, t);
        if (subtitle != null && !subtitle.isBlank()) {
            Label s = new Label(subtitle);
            s.getStyleClass().add("card-subtitle");
            s.setWrapText(true);
            text.getChildren().add(s);
        }
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        row.getChildren().add(text);
        row.getChildren().addAll(trailing);
        return row;
    }

    public static Label badge(String text, String styleClass) {
        Label b = new Label(text);
        b.getStyleClass().addAll("badge", styleClass);
        b.setMinWidth(Region.USE_PREF_SIZE);
        return b;
    }

    public static Label sectionLabel(String text) {
        Label l = new Label(text.toUpperCase(Locale.ROOT));
        l.getStyleClass().add("section-eyebrow");
        return l;
    }

    public static Label muted(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("text-muted-sm");
        l.setWrapText(true);
        return l;
    }

    public static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    /** Runs {@code work} off the FX thread and hands the result back on it. */
    public static <T> void async(Supplier<T> work, Consumer<T> onDone) {
        CompletableFuture.supplyAsync(work).whenComplete((result, error) -> Platform.runLater(() -> {
            if (error != null) {
                NotificationManager.error("Falha: " + rootMessage(error));
                onDone.accept(null);
            } else {
                onDone.accept(result);
            }
        }));
    }

    /** Button action with loading state; the button is re-enabled afterwards. */
    public static <T> void run(ActionButton button, Supplier<T> work, Consumer<T> onDone) {
        button.setLoading(true);
        button.setDisable(true);
        async(work, result -> {
            button.setLoading(false);
            button.setDisable(false);
            onDone.accept(result);
        });
    }

    public static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 MB";
        if (bytes < 1024L * 1024) return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1024) return String.format(Locale.US, "%.2f GB", mb / 1024.0);
        return String.format(Locale.US, "%.0f MB", mb);
    }

    public static String formatGb(long bytes) {
        return String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
