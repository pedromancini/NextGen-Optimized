package com.nextgen.optimizer.ui.pages;

import com.nextgen.optimizer.App;
import com.nextgen.optimizer.services.PrivacyService;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class PrivacyPage extends VBox {
    private record Snapshot(java.util.List<PrivacyService.State> states,
                            java.util.List<Boolean> backups, PrivacyService.RecallState recall) {}
    private final App app;
    private final PrivacyService service;
    private final VBox rows = new VBox(0);
    private final Label feedback = label("Consultando preferências do Windows...", "privacy-feedback");
    private final Button refresh = new Button("Atualizar estado");

    public PrivacyPage(App app) {
        this.app = app;
        service = new PrivacyService(app.getRegistryService(), app.getBackupService(), app.getPowerShellService());
        getStyleClass().addAll("page-container", "privacy-page");
        setSpacing(18);
        getChildren().addAll(label("Privacidade do Windows", "page-title"),
            label("Controle o que o Windows aprende sobre você.", "page-subtitle"));
        TextField search = new TextField();
        search.setPromptText("Buscar: Recall, digitação, publicidade...");
        search.getStyleClass().add("process-search-field");
        HBox.setHgrow(search, Priority.ALWAYS);
        HBox toolbar = new HBox(12, search, refresh);
        refresh.setOnAction(e -> reload());
        search.textProperty().addListener((obs, old, value) -> rows.getChildren().forEach(row -> {
            boolean visible = row.getUserData().toString().contains(value.toLowerCase(java.util.Locale.ROOT));
            row.setVisible(visible);
            row.setManaged(visible);
        }));
        getChildren().addAll(toolbar, feedback, rows);
        VBox recall = section("Windows Recall",
            "Desativa o componente que permite salvar capturas da sua atividade em PCs compatíveis. Pode exigir reinicialização. Para reativar, use Recursos do Windows.");
        recall.setUserData("windows recall capturas snapshots");
        Label recallStatus = label("Consultando...", "privacy-status");
        Button disable = new Button("Desativar Recall");
        disable.setOnAction(e -> run("Desativando Recall. Esta operação pode levar alguns minutos...", service::disableRecall));
        Button features = new Button("Recursos do Windows");
        features.setOnAction(e -> app.getPowerShellService().execute("Start-Process -FilePath 'optionalfeatures.exe'"));
        recall.getChildren().addAll(recallStatus, new FlowPane(10, 10, disable, features));
        rows.getChildren().add(recall);
        recall.getProperties().put("status", recallStatus);
        recall.getProperties().put("action", disable);
        for (var option : service.options()) {
            VBox row = section(option.title(), option.description());
            row.setUserData((option.title() + " " + option.description()).toLowerCase(java.util.Locale.ROOT));
            Label state = label("Consultando...", "privacy-status");
            Button apply = new Button("Desativar coleta");
            if (!option.id().equals("typing")) apply.setText("Desativar personalização");
            Button restore = new Button("Restaurar anterior");
            Hyperlink settings = new Hyperlink("Conferir no Windows");
            settings.setOnAction(e -> app.getHostServices().showDocument(option.uri()));
            apply.setOnAction(e -> run("Salvando backup e aplicando preferências...", () -> service.apply(option)));
            restore.setOnAction(e -> run("Restaurando preferências...", () -> service.restore(option)));
            row.getChildren().addAll(state, new FlowPane(10, 10, apply, restore, settings));
            row.getProperties().put("option", option);
            row.getProperties().put("status", state);
            row.getProperties().put("action", apply);
            row.getProperties().put("restore", restore);
            rows.getChildren().add(row);
        }
        reload();
    }

    private void busy(boolean value) {
        rows.setDisable(value);
        refresh.setDisable(value);
    }

    private void run(String message, Supplier<PrivacyService.Result> action) {
        busy(true);
        feedback.setText(message);
        CompletableFuture.supplyAsync(action).whenComplete((result, error) -> Platform.runLater(() -> {
            String text = error == null ? result.message() : "Falha na operação. Confira as permissões e tente novamente.";
            loadStates(text);
        }));
    }

    private void reload() { loadStates("Estado atualizado. As preferências se aplicam ao usuário que executa o NextGen."); }

    private void loadStates(String completion) {
        busy(true);
        CompletableFuture.supplyAsync(() -> {
            var states = service.options().stream().map(service::state).toList();
            var backups = service.options().stream().map(service::hasBackup).toList();
            var recall = service.recallState();
            return new Snapshot(states, backups, recall);
        }).whenComplete((data, error) -> Platform.runLater(() -> {
            busy(false);
            if (error != null) {
                feedback.setText("Não foi possível consultar o Windows. Tente atualizar novamente.");
                rows.setDisable(true);
                return;
            }
            var states = data.states();
            var backups = data.backups();
            var recall = data.recall();
            VBox first = (VBox) rows.getChildren().get(0);
            ((Label) first.getProperties().get("status")).setText(switch (recall) {
                case ENABLED -> "Componente habilitado • não significa que esteja gravando";
                case DISABLED -> "Componente desativado";
                case ABSENT -> "Não disponível neste Windows";
                case PENDING -> "Reinicialização pendente";
                case UNKNOWN -> "Estado indisponível • verifique como administrador";
            });
            ((Button) first.getProperties().get("action")).setDisable(recall != PrivacyService.RecallState.ENABLED);
            for (int i = 1; i < rows.getChildren().size(); i++) {
                VBox row = (VBox) rows.getChildren().get(i);
                var state = states.get(i - 1);
                ((Label) row.getProperties().get("status")).setText(switch (state) {
                    case CONFIGURED -> "Desativação configurada";
                    case NOT_CONFIGURED -> "Desativação não configurada";
                    case UNKNOWN -> "Preferência não definida ou indisponível";
                });
                ((Button) row.getProperties().get("action")).setDisable(state == PrivacyService.State.CONFIGURED);
                ((Button) row.getProperties().get("restore")).setDisable(!Boolean.TRUE.equals(backups.get(i - 1)));
            }
            feedback.setText(completion);
        }));
    }

    private static VBox section(String title, String description) {
        VBox box = new VBox(10, label(title, "section-title"), label(description, "privacy-description"));
        box.getStyleClass().add("privacy-row");
        return box;
    }

    private static Label label(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }
}
