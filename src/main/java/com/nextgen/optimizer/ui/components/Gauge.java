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
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

/**
 * A circular gauge component that displays a value on an arc.
 * The arc fills from bottom-left (225°) to bottom-right (-45°) based on value/maxValue.
 * Colors shift from green to yellow to red as the value increases.
 */
public class Gauge extends StackPane {

    private final DoubleProperty value = new SimpleDoubleProperty(0);
    private final DoubleProperty maxValue = new SimpleDoubleProperty(100);
    private final StringProperty unit = new SimpleStringProperty("%");
    private final StringProperty label = new SimpleStringProperty("");

    // Internal animated value for smooth transitions
    private final DoubleProperty animatedValue = new SimpleDoubleProperty(0);

    private final Canvas canvas;
    private double gaugeSize;

    private static final double ARC_WIDTH = 10.0;
    private static final Color BG_ARC_COLOR = Color.web("#1e2436");
    private static final double START_ANGLE = 225.0;
    private static final double TOTAL_SWEEP = 270.0; // 225 to -45 = 270 degrees

    public Gauge() {
        this(120);
    }

    public Gauge(double size) {
        this("", "%", 100, size);
    }

    public Gauge(String label, String unit, double maxValue) {
        this(label, unit, maxValue, 120);
    }

    public Gauge(String label, String unit, double maxValue, double size) {
        this.label.set(label);
        this.unit.set(unit);
        this.maxValue.set(maxValue);
        this.gaugeSize = size;
        canvas = new Canvas(size, size);
        setAlignment(Pos.CENTER);
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        getStyleClass().add("gauge");

        getChildren().add(canvas);

        // Redraw whenever animated value, maxValue, unit, or label changes
        animatedValue.addListener((obs, o, n) -> draw());
        this.maxValue.addListener((obs, o, n) -> draw());
        this.unit.addListener((obs, o, n) -> draw());
        this.label.addListener((obs, o, n) -> draw());

        // Animate when real value changes
        value.addListener((obs, oldVal, newVal) -> {
            Timeline timeline = new Timeline(
                    new KeyFrame(Duration.millis(500),
                            new KeyValue(animatedValue, newVal.doubleValue(),
                                    javafx.animation.Interpolator.EASE_BOTH))
            );
            timeline.play();
        });

        draw();
    }

    private void draw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        // Enable anti-aliasing
        gc.clearRect(0, 0, w, h);

        double padding = ARC_WIDTH / 2.0 + 2;
        double arcX = padding;
        double arcY = padding;
        double arcW = w - padding * 2;
        double arcH = h - padding * 2;

        // --- Background arc ---
        gc.setStroke(BG_ARC_COLOR);
        gc.setLineWidth(ARC_WIDTH);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.strokeArc(arcX, arcY, arcW, arcH, START_ANGLE, -TOTAL_SWEEP,
                javafx.scene.shape.ArcType.OPEN);

        // --- Foreground arc ---
        double max = maxValue.get();
        double val = Math.max(0, Math.min(animatedValue.get(), max));
        double ratio = max > 0 ? val / max : 0;
        double sweepAngle = -TOTAL_SWEEP * ratio;

        gc.setStroke(getArcColor(ratio));
        gc.setLineWidth(ARC_WIDTH);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        if (Math.abs(sweepAngle) > 0.5) {
            gc.strokeArc(arcX, arcY, arcW, arcH, START_ANGLE, sweepAngle,
                    javafx.scene.shape.ArcType.OPEN);
        }

        // --- Center text: value ---
        String displayValue = String.valueOf((int) Math.round(animatedValue.get()));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Color.WHITE);

        double valueFontSize = gaugeSize * 0.22;
        gc.setFont(Font.font("System", FontWeight.BOLD, valueFontSize));
        gc.fillText(displayValue, w / 2, h / 2 - 2);

        // --- Unit text ---
        String unitText = unit.get();
        if (unitText != null && !unitText.isEmpty()) {
            double unitFontSize = gaugeSize * 0.11;
            gc.setFont(Font.font("System", FontWeight.NORMAL, unitFontSize));
            gc.setFill(Color.web("#8892b0"));
            gc.fillText(unitText, w / 2, h / 2 + valueFontSize * 0.6);
        }

        // --- Label text (below gauge) ---
        String labelText = label.get();
        if (labelText != null && !labelText.isEmpty()) {
            double labelFontSize = gaugeSize * 0.09;
            gc.setFont(Font.font("System", FontWeight.NORMAL, labelFontSize));
            gc.setFill(Color.web("#8892b0"));
            gc.fillText(labelText, w / 2, h - padding + 2);
        }
    }

    /**
     * Returns a color based on the ratio: green (<50%), yellow (50-80%), red (>80%).
     * Smoothly interpolates between thresholds.
     */
    private Color getArcColor(double ratio) {
        Color green = Color.web("#10b981");
        Color yellow = Color.web("#f59e0b");
        Color red = Color.web("#ef4444");

        if (ratio < 0.5) {
            double t = ratio / 0.5;
            return green.interpolate(yellow, t);
        } else if (ratio < 0.8) {
            double t = (ratio - 0.5) / 0.3;
            return yellow.interpolate(red, t);
        } else {
            return red;
        }
    }

    /**
     * Sets the gauge size and recreates the canvas.
     */
    public void setGaugeSize(double size) {
        this.gaugeSize = size;
        canvas.setWidth(size);
        canvas.setHeight(size);
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        draw();
    }

    // --- Properties ---

    public DoubleProperty valueProperty() {
        return value;
    }

    public double getValue() {
        return value.get();
    }

    public void setValue(double val) {
        value.set(val);
    }

    public DoubleProperty maxValueProperty() {
        return maxValue;
    }

    public double getMaxValue() {
        return maxValue.get();
    }

    public void setMaxValue(double val) {
        maxValue.set(val);
    }

    public StringProperty unitProperty() {
        return unit;
    }

    public String getUnit() {
        return unit.get();
    }

    public void setUnit(String val) {
        unit.set(val);
    }

    public StringProperty labelProperty() {
        return label;
    }

    public String getLabel() {
        return label.get();
    }

    public void setLabel(String val) {
        label.set(val);
    }
}
