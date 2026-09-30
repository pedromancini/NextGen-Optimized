package com.nextgen.optimizer.ui.components;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

import java.util.List;

/**
 * Card grid that picks its column count from the available width: children
 * keep at least {@code minColumnWidth} and share the row equally. Columns
 * collapse one by one as the window narrows, down to a single column.
 * A child may span several columns with {@link #setSpan(Node, int)}.
 */
public class ResponsiveGrid extends Pane {

    private static final String SPAN_KEY = "responsive-grid-span";

    private final double minColumnWidth;
    private final int maxColumns;
    private double hgap = 16;
    private double vgap = 16;

    public ResponsiveGrid(double minColumnWidth, int maxColumns, Node... children) {
        this.minColumnWidth = minColumnWidth;
        this.maxColumns = Math.max(1, maxColumns);
        getStyleClass().add("responsive-grid");
        getChildren().addAll(children);
    }

    public ResponsiveGrid gaps(double h, double v) {
        this.hgap = h;
        this.vgap = v;
        requestLayout();
        return this;
    }

    public static void setSpan(Node node, int span) {
        node.getProperties().put(SPAN_KEY, Math.max(1, span));
    }

    private static int span(Node node, int columns) {
        Object v = node.getProperties().get(SPAN_KEY);
        return Math.min(columns, v instanceof Integer i ? i : 1);
    }

    public int columnsFor(double width) {
        Insets in = getInsets();
        double usable = width - in.getLeft() - in.getRight();
        int cols = (int) Math.floor((usable + hgap) / (minColumnWidth + hgap));
        return Math.max(1, Math.min(maxColumns, cols));
    }

    @Override
    protected double computeMinWidth(double height) {
        Insets in = getInsets();
        return Math.min(minColumnWidth, 240) + in.getLeft() + in.getRight();
    }

    @Override
    protected double computePrefWidth(double height) {
        Insets in = getInsets();
        return minColumnWidth * maxColumns + hgap * (maxColumns - 1) + in.getLeft() + in.getRight();
    }

    @Override
    protected double computePrefHeight(double width) {
        if (width <= 0) width = getWidth() > 0 ? getWidth() : computePrefWidth(-1);
        return layoutRows(width, false);
    }

    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    @Override
    protected void layoutChildren() {
        layoutRows(getWidth(), true);
    }

    /** Lays out (or measures) rows; returns the total height. */
    private double layoutRows(double width, boolean apply) {
        Insets in = getInsets();
        int columns = columnsFor(width);
        double usable = Math.max(0, width - in.getLeft() - in.getRight());
        double colWidth = (usable - hgap * (columns - 1)) / columns;

        List<Node> managed = getManagedChildren();
        double y = in.getTop();
        int i = 0;
        while (i < managed.size()) {
            // Collect one row.
            int used = 0;
            int start = i;
            while (i < managed.size()) {
                int s = span(managed.get(i), columns);
                if (used > 0 && used + s > columns) break;
                used += s;
                i++;
            }
            double rowHeight = 0;
            for (int k = start; k < i; k++) {
                Node n = managed.get(k);
                double w = colWidth * span(n, columns) + hgap * (span(n, columns) - 1);
                rowHeight = Math.max(rowHeight, n.prefHeight(w));
            }
            if (apply) {
                double x = in.getLeft();
                for (int k = start; k < i; k++) {
                    Node n = managed.get(k);
                    int s = span(n, columns);
                    double w = colWidth * s + hgap * (s - 1);
                    n.resizeRelocate(snapPositionX(x), snapPositionY(y), snapSizeX(w), snapSizeY(rowHeight));
                    x += w + hgap;
                }
            }
            y += rowHeight + (i < managed.size() ? vgap : 0);
        }
        return y + in.getBottom();
    }
}
