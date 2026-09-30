package com.nextgen.optimizer.ui.components;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

/**
 * A circular progress ring indicator rendered on a Canvas.
 * Progress 0.0–1.0 with colour thresholds:
 *   blue (#3b82f6) < 0.5,  yellow (#f59e0b) 0.5–0.8,  red (#ef4444) > 0.8.
 */
public class ProgressRing extends StackPane {

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private final StringProperty centerText = new SimpleStringProperty("");
    private final StringProperty subText = new SimpleStringProperty("");

    // Internal animated value for smooth transitions
    private final DoubleProperty animatedProgress = new SimpleDoubleProperty(0);

    private final Canvas canvas;
    private double ringSize;

    private static final double STROKE_WIDTH = 8.0;
    private static final Color BG_RING_COLOR = Color.web("#1b2233");
    private static final Color BLUE = Color.web("#3b82f6");
    private static final Color YELLOW = Color.web("#f59e0b");
    private static final Color RED = Color.web("#ef4444");

    public ProgressRing() {
        this(100);
    }

    public ProgressRing(double size) {
        this.ringSize = size;
        canvas = new Canvas(size, size);
        setAlignment(Pos.CENTER);
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        getStyleClass().add("progress-ring");

        getChildren().add(canvas);

        // Redraw on any visual property change
        animatedProgress.addListener((obs, o, n) -> draw());
        centerText.addListener((obs, o, n) -> draw());
        subText.addListener((obs, o, n) -> draw());

        // Animate when progress property changes
        progress.addListener((obs, oldVal, newVal) -> {
            double clamped = Math.max(0.0, Math.min(1.0, newVal.doubleValue()));
            Timeline tl = new Timeline(
                    new KeyFrame(Duration.millis(400),
                            new KeyValue(animatedProgress, clamped,
                                    javafx.animation.Interpolator.EASE_BOTH))
            );
            tl.play();
        });

        draw();
    }

    private void draw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        gc.clearRect(0, 0, w, h);

        double pad = STROKE_WIDTH / 2.0 + 2;
        double arcX = pad;
        double arcY = pad;
        double arcW = w - pad * 2;
        double arcH = h - pad * 2;

        // --- Background ring (full circle) ---
        gc.setStroke(BG_RING_COLOR);
        gc.setLineWidth(STROKE_WIDTH);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.strokeArc(arcX, arcY, arcW, arcH, 90, -360, ArcType.OPEN);

        // --- Foreground ring ---
        double prog = Math.max(0, Math.min(1.0, animatedProgress.get()));
        if (prog > 0.001) {
            double sweepAngle = -360.0 * prog;
            gc.setStroke(getRingPaint(prog));
            if (positive) gc.setEffect(new javafx.scene.effect.DropShadow(12, Color.web("#22d3ee66")));
            gc.setLineWidth(STROKE_WIDTH);
            gc.setLineCap(StrokeLineCap.ROUND);
            gc.strokeArc(arcX, arcY, arcW, arcH, 90, sweepAngle, ArcType.OPEN);
            gc.setEffect(null);
        }

        // --- Center text ---
        String center = centerText.get();
        if (center != null && !center.isEmpty()) {
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setFill(Color.WHITE);
            double fontSize = ringSize * 0.22;
            gc.setFont(Font.font("System", FontWeight.BOLD, fontSize));
            gc.fillText(center, w / 2, h / 2 + fontSize * 0.15);
        }

        // --- Sub text ---
        String sub = subText.get();
        if (sub != null && !sub.isEmpty()) {
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setFill(Color.web("#8b95ad"));
            double subFontSize = ringSize * 0.11;
            gc.setFont(Font.font("System", FontWeight.NORMAL, subFontSize));
            gc.fillText(sub, w / 2, h / 2 + ringSize * 0.2);
        }
    }

    private boolean positive;

    /** When true, a fuller ring is better (e.g. an optimization score): drawn with the accent gradient. */
    public void setPositive(boolean positive) {
        this.positive = positive;
        draw();
    }

    private javafx.scene.paint.Paint getRingPaint(double prog) {
        if (!positive) return getRingColor(prog);
        return new javafx.scene.paint.LinearGradient(0, 0, 1, 1, true, javafx.scene.paint.CycleMethod.NO_CYCLE,
                new javafx.scene.paint.Stop(0, Color.web("#22d3ee")), new javafx.scene.paint.Stop(1, Color.web("#a78bfa")));
    }

    private Color getRingColor(double prog) {
        if (prog < 0.5) {
            return BLUE;
        } else if (prog <= 0.8) {
            return YELLOW;
        } else {
            return RED;
        }
    }

    /**
     * Sets the ring size and recreates the canvas.
     */
    public void setRingSize(double size) {
        this.ringSize = size;
        canvas.setWidth(size);
        canvas.setHeight(size);
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        draw();
    }

    // --- Properties ---

    public DoubleProperty progressProperty() {
        return progress;
    }

    public double getProgress() {
        return progress.get();
    }

    public void setProgress(double value) {
        progress.set(value);
    }

    public StringProperty centerTextProperty() {
        return centerText;
    }

    public String getCenterText() {
        return centerText.get();
    }

    public void setCenterText(String text) {
        centerText.set(text);
    }

    public StringProperty subTextProperty() {
        return subText;
    }

    public String getSubText() {
        return subText.get();
    }

    public void setSubText(String text) {
        subText.set(text);
    }
}
