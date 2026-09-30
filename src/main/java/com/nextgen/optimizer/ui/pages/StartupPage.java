package com.nextgen.optimizer.ui.pages;

import com.nextgen.optimizer.App;
import com.nextgen.optimizer.core.NotificationManager;
import com.nextgen.optimizer.services.StartupService;
import com.nextgen.optimizer.ui.components.*;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.List;

/**
 * Startup programs: fewer programs at boot means more free RAM and fewer
 * background processes competing with the game.
 */
public class StartupPage extends VBox {

    private final App app;
    private final StartupService service;
    private final VBox list = new VBox(0);
    private final Label summary = new Label();

    public StartupPage(App app) {
        this.app = app;
        this.service = new StartupService(app.getRegistryService());
        getStyleClass().add("page-container");
        setSpacing(18);

        ActionButton refresh = new ActionButton("Atualizar", "default");
        refresh.setOnAction(e -> reload());
        list.getStyleClass().add("tweak-list");
        summary.getStyleClass().add("card-subtitle");

        VBox card = Ui.card();
        card.getChildren().addAll(Ui.cardHeader("mdi2p-power", "Programas que abrem com o Windows", null, refresh), summary, list);

        getChildren().addAll(
                Ui.pageHeader("mdi2p-power", "Inicialização",
                        "Desative o que não precisa abrir junto com o Windows. O programa continua instalado e pode ser religado a qualquer momento."),
                card);
        reload();
    }

    private void reload() {
        summary.setText("Carregando…");
        Ui.async(service::list, items -> {
            if (items == null) return;
            render(items);
        });
    }

    private void render(List<StartupService.Item> items) {
        list.getChildren().clear();
        long enabled = items.stream().filter(StartupService.Item::enabled).count();
        summary.setText(items.size() + " programas · " + enabled + " ativos na inicialização");
        if (items.isEmpty()) {
            list.getChildren().add(Ui.muted("Nenhum programa de inicialização encontrado."));
        }
        for (StartupService.Item item : items) {
            Label name = new Label(item.name());
            name.getStyleClass().add("tweak-title");
            HBox titleRow = new HBox(8, name, Ui.badge(item.location(), "badge-muted"));
            if (item.commonlyDisabled()) titleRow.getChildren().add(Ui.badge("Pode desativar", "badge-impact"));
            titleRow.setAlignment(Pos.CENTER_LEFT);
            Label cmd = new Label(item.command());
            cmd.getStyleClass().add("mono-label");
            cmd.setMaxWidth(Double.MAX_VALUE);
            VBox text = new VBox(3, titleRow, cmd);
            text.setMinWidth(0);
            HBox.setHgrow(text, Priority.ALWAYS);

            ToggleSwitch toggle = new ToggleSwitch(item.enabled());
            toggle.setDisable(!app.isElevatedProcess() && item.approvedRoot() != com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER);
            toggle.setOnAction(e -> {
                boolean on = toggle.isSelected();
                if (service.setEnabled(item, on)) {
                    NotificationManager.show(item.name() + (on ? " será aberto com o Windows." : " não abrirá mais com o Windows."),
                            NotificationManager.Type.SUCCESS);
                } else {
                    toggle.setSelected(!on);
                    NotificationManager.warning("Não foi possível alterar " + item.name() + ".");
                }
            });

            HBox row = new HBox(14, text, toggle);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("tweak-row");
            list.getChildren().add(row);
        }
    }
}
