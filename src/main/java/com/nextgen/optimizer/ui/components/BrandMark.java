package com.nextgen.optimizer.ui.components;

import javafx.scene.effect.DropShadow;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;

/** Vector brand mark: gradient hexagon with the "X" monogram. Crisp at any size. */
public class BrandMark extends StackPane {

    public BrandMark(double size) {
        double s = size / 24.0;
        SVGPath hex = new SVGPath();
        hex.setContent("M12 1.5 L21.5 7 L21.5 17 L12 22.5 L2.5 17 L2.5 7 Z");
        hex.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#22d3ee")), new Stop(1, Color.web("#8b5cf6"))));
        hex.setScaleX(s);
        hex.setScaleY(s);

        SVGPath x = new SVGPath();
        x.setContent("M8 7.5 L16 16.5 M16 7.5 L8 16.5");
        x.setStroke(Color.web("#06080d"));
        x.setStrokeWidth(2.6);
        x.setStrokeLineCap(StrokeLineCap.ROUND);
        x.setFill(null);
        x.setScaleX(s);
        x.setScaleY(s);

        getChildren().addAll(hex, x);
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        setEffect(new DropShadow(size * 0.5, Color.web("#22d3ee55")));
    }
}
