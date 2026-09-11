package com.nextgen.optimizer.ui.pages;

import com.nextgen.optimizer.App;
import com.nextgen.optimizer.services.BackupService.BackupSnapshot;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

public class SettingsPage extends VBox {
    private final App app;
    private final ListView<BackupSnapshot> list = new ListView<>();
    private final Label status = new Label("Consultando backups...");
    private final Button restore = new Button("Restaurar selecionado");
    private final Button refresh = new Button("Atualizar lista");

    public SettingsPage(App app) {
        this.app = app;
        setSpacing(16);
        getStyleClass().addAll("page-container", "privacy-page");
        Label title = new Label("Configurações e restauração");
        title.getStyleClass().add("page-title");
        Label description = new Label("Backups dos valores alterados pelo NextGen. Eles não substituem um ponto de restauração completo do Windows.");
        description.setWrapText(true);
        description.getStyleClass().add("privacy-description");
        status.getStyleClass().add("privacy-feedback");
        status.setWrapText(true);
        list.setPrefHeight(300);
        list.setPlaceholder(new Label("Nenhum backup salvo pelo NextGen."));
        list.setCellFactory(view -> new ListCell<>() {
            protected void updateItem(BackupSnapshot item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(item.getTimestamp()))
                    + "   |   " + item.getLabel() + "   |   " + item.getEntries().size() + " valores");
                setWrapText(true);
            }
        });
        restore.setDisable(true);
        list.getSelectionModel().selectedItemProperty().addListener((o, old, item) -> restore.setDisable(item == null));
        restore.setOnAction(e -> restoreSelected());
        refresh.setOnAction(e -> reload());
        Button folder = new Button("Abrir pasta de backups");
        folder.setOnAction(e -> {
            try {
                var path = app.getBackupService().getBackupDir();
                java.nio.file.Files.createDirectories(path);
                new ProcessBuilder("explorer.exe", path.toString()).start();
            } catch (Exception ex) { status.setText("Não foi possível abrir a pasta de backups."); }
        });
        getChildren().addAll(title, description, new FlowPane(10, 10, refresh, folder, restore), status, list);
        reload();
    }

    private void reload() {
        refresh.setDisable(true);
        CompletableFuture.supplyAsync(() -> app.getBackupService().listBackups()).whenComplete((items, error) -> Platform.runLater(() -> {
            refresh.setDisable(false);
            if (error == null) {
                list.getItems().setAll(items);
                status.setText(items.size() + " backups disponíveis.");
            } else status.setText("Falha ao consultar backups.");
        }));
    }

    private void restoreSelected() {
        BackupSnapshot selected = list.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
            "Restaurar os valores salvos em '" + selected.getLabel() + "'? Isso pode substituir ajustes feitos depois desse backup.",
            ButtonType.CANCEL, ButtonType.OK);
        confirmation.initOwner(getScene().getWindow());
        confirmation.setHeaderText("Restaurar ajustes anteriores");
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        list.setDisable(true); restore.setDisable(true); refresh.setDisable(true);
        status.setText("Restaurando...");
        CompletableFuture.supplyAsync(() -> app.getBackupService().restoreBackup(selected))
            .whenComplete((ok, error) -> Platform.runLater(() -> {
                list.setDisable(false); restore.setDisable(false); refresh.setDisable(false);
                status.setText(error == null && Boolean.TRUE.equals(ok)
                    ? "Backup restaurado. Atualize a página do ajuste para conferir o estado."
                    : "Restauração incompleta. Verifique as permissões e tente novamente.");
            }));
    }
}
