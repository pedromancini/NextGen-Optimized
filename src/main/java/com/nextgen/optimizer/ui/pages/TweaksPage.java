package com.nextgen.optimizer.ui.pages;

import com.nextgen.optimizer.App;
import com.nextgen.optimizer.core.NotificationManager;
import com.nextgen.optimizer.tweaks.Tweak;
import com.nextgen.optimizer.tweaks.TweakService;
import com.nextgen.optimizer.ui.components.*;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every optimization in one place: searchable, filterable by category, each
 * with a switch that applies or reverts it and an explanation of its effect.
 */
public class TweaksPage extends VBox {

    private final App app;
    private final TextField search = new TextField();
    private final FlowPane filters = new FlowPane(8, 8);
    private final VBox groups = new VBox(16);
    private final Label summary = new Label();
    private final ToggleSwitch showAdvanced = new ToggleSwitch(true);
    private final Map<Tweak.Category, VBox> groupCards = new EnumMap<>(Tweak.Category.class);
    private final List<TweakRow> rows = new ArrayList<>();
    private Tweak.Category activeCategory;

    public TweaksPage(App app) {
        this.app = app;
        getStyleClass().add("page-container");
        setSpacing(16);

        search.setPromptText("Buscar ajuste (ex.: mouse, energia, telemetria)…");
        search.getStyleClass().add("search-field");
        search.setPrefWidth(320);
        search.textProperty().addListener((o, a, b) -> applyFilter());

        getChildren().addAll(
                Ui.pageHeader("mdi2t-tune-variant", "Central de Ajustes",
                        "Todos os ajustes do Windows em um só lugar. Cada um salva o valor original e pode ser desfeito a qualquer momento."),
                buildToolbar(),
                groups);

        buildGroups();
        buildFilters();
        applyFilter();
        if (!app.isElevatedProcess()) {
            NotificationManager.warning("Sem administrador: os ajustes só podem ser consultados.");
        }
    }

    private VBox buildToolbar() {
        summary.getStyleClass().add("card-subtitle");
        showAdvanced.setText("Mostrar avançados");
        showAdvanced.setOnAction(e -> applyFilter());
        HBox top = new HBox(12, search, Ui.spacer(), showAdvanced);
        top.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(12, top, filters, summary);
        box.getStyleClass().add("toolbar");
        return box;
    }

    private void buildFilters() {
        filters.getChildren().clear();
        filters.getChildren().add(filterChip(null, "Todos", "mdi2a-apps"));
        for (Tweak.Category c : Tweak.Category.values()) {
            filters.getChildren().add(filterChip(c, c.label, c.icon));
        }
    }

    private Label filterChip(Tweak.Category category, String text, String icon) {
        Label chip = new Label(text, Ui.icon(icon, 14));
        chip.getStyleClass().add("filter-chip");
        if (category == activeCategory) chip.getStyleClass().add("filter-chip-active");
        chip.setOnMouseClicked(e -> {
            activeCategory = category;
            buildFilters();
            applyFilter();
        });
        return chip;
    }

    private void buildGroups() {
        Map<Tweak.Category, List<Tweak>> byCategory = new EnumMap<>(Tweak.Category.class);
        for (Tweak t : app.getTweakService().catalog()) {
            byCategory.computeIfAbsent(t.category(), k -> new ArrayList<>()).add(t);
        }
        for (var entry : byCategory.entrySet()) {
            Tweak.Category category = entry.getKey();
            ActionButton applyRecommended = new ActionButton("Aplicar recomendados", "default");
            applyRecommended.setDisable(!app.isElevatedProcess());
            applyRecommended.setOnAction(e -> applyRecommended(category, applyRecommended));

            VBox card = Ui.card("tweak-group");
            card.getChildren().add(Ui.cardHeader(category.icon, category.label,
                    entry.getValue().size() + " ajustes", applyRecommended));
            VBox list = new VBox(0);
            list.getStyleClass().add("tweak-list");
            for (Tweak t : entry.getValue()) {
                TweakRow row = new TweakRow(t, app.getTweakService(), this::onTweakChanged);
                if (!app.isElevatedProcess()) row.setDisable(true);
                rows.add(row);
                list.getChildren().add(row);
            }
            card.getChildren().add(list);
            groupCards.put(category, card);
            groups.getChildren().add(card);
        }
    }

    private void applyRecommended(Tweak.Category category, ActionButton button) {
        List<Tweak> targets = app.getTweakService().catalog().stream()
                .filter(t -> t.category() == category && Tweak.Profile.GAMER.includes(t.profile()))
                .toList();
        Ui.run(button, () -> app.getTweakService().applyAll(targets, null), results -> {
            if (results == null) return;
            long applied = results.stream().filter(TweakService.Result::changed).count();
            long failed = results.stream().filter(r -> !r.success()).count();
            NotificationManager.show(category.label + ": " + applied + " ajustes aplicados"
                            + (failed > 0 ? ", " + failed + " falharam" : ""),
                    failed == 0 ? NotificationManager.Type.SUCCESS : NotificationManager.Type.WARNING);
            rows.stream().filter(r -> r.tweak().category() == category).forEach(TweakRow::refresh);
            onTweakChanged();
        });
    }

    private void onTweakChanged() {
        app.getNavigationManager().invalidate("dashboard", "full-opt");
    }

    private void applyFilter() {
        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        boolean advanced = showAdvanced.isSelected();
        int visible = 0;
        for (TweakRow row : rows) {
            Tweak t = row.tweak();
            boolean show = (activeCategory == null || t.category() == activeCategory)
                    && (q.isEmpty() || t.searchText().contains(q))
                    && (advanced || t.risk() != Tweak.Risk.ADVANCED);
            row.setVisible(show);
            row.setManaged(show);
            if (show) visible++;
        }
        for (var e : groupCards.entrySet()) {
            boolean any = rows.stream().anyMatch(r -> r.tweak().category() == e.getKey() && r.isVisible());
            e.getValue().setVisible(any);
            e.getValue().setManaged(any);
        }
        summary.setText(visible + " de " + rows.size() + " ajustes · " + app.getTweakService().appliedByNextGenCount()
                + " aplicados pelo NextGen X (reversíveis)");
    }
}
