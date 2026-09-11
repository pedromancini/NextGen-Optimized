package com.nextgen.optimizer.ui.components;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignI;

/**
 * A compact metric display card showing an icon, label, value, and sub-text.
 * Designed for dashboard-style metric grids.
 */
public class MetricCard extends VBox {

    private final StringProperty valueProperty = new SimpleStringProperty("");
    private final StringProperty subTextProperty = new SimpleStringProperty("");

    private final Label iconLabel;
    private final Label labelLabel;
    private final Label valueLabel;
    private final Label subLabel;

    /**
     * Creates a MetricCard.
     *
     * @param icon         Ikonli icon literal (e.g. "mdi2c-cpu-64-bit") or plain text emoji/symbol
     * @param label        The metric label (e.g. "CPU Usage")
     * @param initialValue The initial display value (e.g. "45%")
     * @param subText      Additional context text (e.g. "Intel Core i7")
     */
    public MetricCard(String icon, String label, String initialValue, String subText) {
        getStyleClass().add("metric-card");
        setAlignment(Pos.TOP_LEFT);
        setSpacing(4);
        setPadding(new Insets(16));

        // --- Icon ---
        iconLabel = new Label();
        iconLabel.getStyleClass().add("metric-card-icon");
        try {
            FontIcon fontIcon = new FontIcon(icon);
            fontIcon.getStyleClass().add("metric-card-icon");
            iconLabel.setGraphic(fontIcon);
        } catch (Exception e) {
            // Fallback: use the string as text if it's not a valid Ikonli literal
            iconLabel.setText(icon);
        }

        // --- Label ---
        labelLabel = new Label(label);
        labelLabel.getStyleClass().add("metric-card-label");

        // --- Value ---
        valueLabel = new Label(initialValue);
        valueLabel.getStyleClass().add("metric-card-value");
        valueLabel.textProperty().bind(valueProperty);
        valueProperty.set(initialValue);

        // --- Sub text ---
        subLabel = new Label(subText);
        subLabel.getStyleClass().add("metric-card-sub");
        subLabel.textProperty().bind(subTextProperty);
        subTextProperty.set(subText);

        getChildren().addAll(iconLabel, labelLabel, valueLabel, subLabel);
    }

    // --- Properties ---

    public StringProperty valueProperty() {
        return valueProperty;
    }

    public String getValue() {
        return valueProperty.get();
    }

    public void setValue(String value) {
        valueProperty.set(value);
    }

    public StringProperty subTextProperty() {
        return subTextProperty;
    }

    public String getSubText() {
        return subTextProperty.get();
    }

    public void setSubText(String text) {
        subTextProperty.set(text);
    }
}
