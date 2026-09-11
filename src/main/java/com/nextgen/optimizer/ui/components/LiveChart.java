package com.nextgen.optimizer.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A high-performance real-time line chart rendered on a Canvas.
 * Supports multiple named data series with independent colors, auto-scaling Y-axis,
 * and a scrolling window that shows the most recent data points.
 */
public class LiveChart extends VBox {

    private static final int MAX_DATA_POINTS = 60;
    private static final Color GRID_COLOR = Color.web("#1e2436");
    private static final Color AXIS_TEXT_COLOR = Color.web("#8892b0");
    private static final Color BG_COLOR = Color.TRANSPARENT;

    private final Canvas canvas;
    private final StackPane canvasContainer;
    private final HBox legendBox;
    private final Map<String, Series> seriesMap = new LinkedHashMap<>();
    private double chartHeight;

    /**
     * A named data series with its own color and data buffer.
     */
    private static class Series {
        final String name;
        final Color color;
        final List<Double> data = new ArrayList<>();

        Series(String name, Color color) {
            this.name = name;
            this.color = color;
        }
    }

    public LiveChart() {
        this(200);
    }

    public LiveChart(double height) {
        this.chartHeight = height;
        getStyleClass().add("live-chart-container");
        setSpacing(6);

        // --- Canvas container (allows resize) ---
        canvas = new Canvas(400, height);
        canvasContainer = new StackPane(canvas);
        canvasContainer.getStyleClass().add("live-chart-canvas");
        canvasContainer.setMinHeight(height);
        canvasContainer.setPrefHeight(height);
        VBox.setVgrow(canvasContainer, Priority.ALWAYS);

        // Bind canvas size to container size
        canvasContainer.widthProperty().addListener((obs, o, n) -> {
            canvas.setWidth(n.doubleValue());
            draw();
        });
        canvasContainer.heightProperty().addListener((obs, o, n) -> {
            canvas.setHeight(n.doubleValue());
            draw();
        });

        // --- Legend ---
        legendBox = new HBox(16);
        legendBox.getStyleClass().add("live-chart-legend");
        legendBox.setAlignment(Pos.CENTER_LEFT);
        legendBox.setPadding(new Insets(4, 0, 0, 0));

        getChildren().addAll(canvasContainer, legendBox);

        draw();
    }

    /**
     * Adds a new data series with the given name and color.
     */
    public void addSeries(String name, Color color) {
        if (seriesMap.containsKey(name)) return;
        seriesMap.put(name, new Series(name, color));
        rebuildLegend();
    }

    /**
     * Appends a data point to the named series and redraws.
     */
    public void addDataPoint(String seriesName, double value) {
        Series s = seriesMap.get(seriesName);
        if (s == null) return;

        s.data.add(value);
        // Trim to window size
        while (s.data.size() > MAX_DATA_POINTS) {
            s.data.remove(0);
        }
        draw();
    }

    /**
     * Clears all data from every series.
     */
    public void clearAll() {
        seriesMap.values().forEach(s -> s.data.clear());
        draw();
    }

    /**
     * Sets the chart display height.
     */
    public void setChartHeight(double height) {
        this.chartHeight = height;
        canvasContainer.setMinHeight(height);
        canvasContainer.setPrefHeight(height);
    }

    // ---- Drawing ----

    private void draw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        if (w <= 0 || h <= 0) return;

        gc.clearRect(0, 0, w, h);

        double paddingLeft = 45;
        double paddingRight = 10;
        double paddingTop = 10;
        double paddingBottom = 20;
        double chartW = w - paddingLeft - paddingRight;
        double chartH = h - paddingTop - paddingBottom;

        if (chartW <= 0 || chartH <= 0) return;

        // --- Compute Y range across all series ---
        double yMin = Double.MAX_VALUE;
        double yMax = Double.MIN_VALUE;
        boolean hasData = false;

        for (Series s : seriesMap.values()) {
            for (Double v : s.data) {
                if (v < yMin) yMin = v;
                if (v > yMax) yMax = v;
                hasData = true;
            }
        }

        if (!hasData) {
            yMin = 0;
            yMax = 100;
        }

        // Add 10% headroom
        double range = yMax - yMin;
        if (range < 1) range = 1;
        yMin = Math.max(0, yMin - range * 0.05);
        yMax = yMax + range * 0.1;
        range = yMax - yMin;

        // --- Grid lines (horizontal) ---
        gc.setStroke(GRID_COLOR);
        gc.setLineWidth(1);
        gc.setLineDashes(null);

        int gridLines = 5;
        gc.setFont(Font.font("System", FontWeight.NORMAL, 10));
        gc.setFill(AXIS_TEXT_COLOR);
        gc.setTextAlign(TextAlignment.RIGHT);

        for (int i = 0; i <= gridLines; i++) {
            double ratio = (double) i / gridLines;
            double y = paddingTop + chartH * ratio;
            double val = yMax - ratio * range;

            gc.strokeLine(paddingLeft, y, w - paddingRight, y);

            String label = val >= 1000 ? String.format("%.0f", val)
                    : val >= 100 ? String.format("%.0f", val)
                    : val >= 10 ? String.format("%.1f", val)
                    : String.format("%.1f", val);
            gc.fillText(label, paddingLeft - 6, y + 4);
        }

        // --- Vertical grid lines ---
        gc.setStroke(GRID_COLOR);
        int vLines = 6;
        for (int i = 0; i <= vLines; i++) {
            double x = paddingLeft + chartW * ((double) i / vLines);
            gc.strokeLine(x, paddingTop, x, paddingTop + chartH);
        }

        // --- Draw series ---
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineWidth(2);

        for (Series s : seriesMap.values()) {
            if (s.data.isEmpty()) continue;

            gc.setStroke(s.color);
            int count = s.data.size();
            int maxPts = Math.min(count, MAX_DATA_POINTS);
            int startIdx = count - maxPts;

            double stepX = maxPts > 1 ? chartW / (MAX_DATA_POINTS - 1) : 0;

            // Draw filled area under the line (subtle)
            gc.setGlobalAlpha(0.08);
            gc.setFill(s.color);
            gc.beginPath();
            for (int i = 0; i < maxPts; i++) {
                double val = s.data.get(startIdx + i);
                double x = paddingLeft + (MAX_DATA_POINTS - maxPts + i) * stepX;
                double y = paddingTop + chartH * (1.0 - (val - yMin) / range);
                if (i == 0) {
                    gc.moveTo(x, y);
                } else {
                    gc.lineTo(x, y);
                }
            }
            // Close the path along the bottom
            double lastX = paddingLeft + (MAX_DATA_POINTS - 1) * stepX;
            double firstX = paddingLeft + (MAX_DATA_POINTS - maxPts) * stepX;
            gc.lineTo(lastX, paddingTop + chartH);
            gc.lineTo(firstX, paddingTop + chartH);
            gc.closePath();
            gc.fill();
            gc.setGlobalAlpha(1.0);

            // Draw the line
            gc.beginPath();
            for (int i = 0; i < maxPts; i++) {
                double val = s.data.get(startIdx + i);
                double x = paddingLeft + (MAX_DATA_POINTS - maxPts + i) * stepX;
                double y = paddingTop + chartH * (1.0 - (val - yMin) / range);
                if (i == 0) {
                    gc.moveTo(x, y);
                } else {
                    gc.lineTo(x, y);
                }
            }
            gc.stroke();

            // Draw a dot at the latest point
            if (maxPts > 0) {
                double latestVal = s.data.get(count - 1);
                double latestX = paddingLeft + (MAX_DATA_POINTS - 1) * stepX;
                double latestY = paddingTop + chartH * (1.0 - (latestVal - yMin) / range);
                gc.setFill(s.color);
                gc.fillOval(latestX - 3, latestY - 3, 6, 6);
            }
        }
    }

    private void rebuildLegend() {
        legendBox.getChildren().clear();
        for (Series s : seriesMap.values()) {
            HBox item = new HBox(5);
            item.getStyleClass().add("live-chart-legend-item");
            item.setAlignment(Pos.CENTER_LEFT);

            Circle dot = new Circle(4);
            dot.setFill(s.color);
            dot.getStyleClass().add("live-chart-legend-dot");

            Label lbl = new Label(s.name);
            lbl.getStyleClass().add("live-chart-legend-label");

            item.getChildren().addAll(dot, lbl);
            legendBox.getChildren().add(item);
        }
    }
}
