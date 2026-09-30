package com.nextgen.optimizer.ui.components;

import com.nextgen.optimizer.services.MemoryService;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Stacked bar showing physical memory composition like Task Manager:
 * in use / modified / standby (cache) / free.
 */
public class MemoryBar extends VBox {

    private final Region inUse = segment("mem-seg-inuse");
    private final Region modified = segment("mem-seg-modified");
    private final Region standby = segment("mem-seg-standby");
    private final Region free = segment("mem-seg-free");
    private final HBox bar = new HBox(inUse, modified, standby, free);
    private final Label inUseLbl = legend("mem-dot-inuse");
    private final Label modifiedLbl = legend("mem-dot-modified");
    private final Label standbyLbl = legend("mem-dot-standby");
    private final Label freeLbl = legend("mem-dot-free");
    private MemoryService.MemoryState last;

    public MemoryBar() {
        setSpacing(10);
        bar.getStyleClass().add("mem-bar");
        bar.setMinHeight(18);
        bar.setPrefHeight(18);
        bar.widthProperty().addListener((o, a, b) -> layoutSegments());
        FlowPane legendRow = new FlowPane(18, 6, inUseLbl, modifiedLbl, standbyLbl, freeLbl);
        legendRow.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(bar, legendRow);
    }

    private static Region segment(String style) {
        Region r = new Region();
        r.getStyleClass().addAll("mem-seg", style);
        r.setMinWidth(0);
        return r;
    }

    private static Label legend(String dotStyle) {
        Region dot = new Region();
        dot.getStyleClass().addAll("mem-dot", dotStyle);
        Label l = new Label("", dot);
        l.getStyleClass().add("mem-legend");
        return l;
    }

    public void update(MemoryService.MemoryState s) {
        last = s;
        inUseLbl.setText("Em uso  " + Ui.formatGb(s.inUse()));
        modifiedLbl.setText("Modificada  " + Ui.formatBytes(s.modified()));
        standbyLbl.setText("Standby (cache)  " + Ui.formatGb(s.standby()));
        freeLbl.setText("Livre  " + Ui.formatGb(s.free()));
        layoutSegments();
    }

    private void layoutSegments() {
        if (last == null || last.total() <= 0) return;
        double w = bar.getWidth();
        double total = last.total();
        inUse.setPrefWidth(w * last.inUse() / total);
        modified.setPrefWidth(w * last.modified() / total);
        standby.setPrefWidth(w * last.standby() / total);
        free.setPrefWidth(Math.max(0, w - inUse.getPrefWidth() - modified.getPrefWidth() - standby.getPrefWidth()));
    }
}
