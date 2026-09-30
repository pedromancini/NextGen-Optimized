package com.nextgen.optimizer.ui.pages;

import com.nextgen.optimizer.App;
import com.nextgen.optimizer.core.Brand;
import com.nextgen.optimizer.core.NotificationManager;
import com.nextgen.optimizer.services.BackupService.BackupSnapshot;
import com.nextgen.optimizer.ui.components.*;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Safety center: restore points, one-click revert of every NextGen X tweak,
 * legacy backups and app preferences.
 */
public class SettingsPage extends VBox {

    private final App app;
    private final ListView<BackupSnapshot> list = new ListView<>();
    private final Label backupStatus = new Label("Consultando backups…");
    private final Label appliedLabel = new Label();

    public SettingsPage(App app) {
        this.app = app;
        getStyleClass().addAll("page-container", "privacy-page");
        setSpacing(18);

        getChildren().addAll(
                Ui.pageHeader("mdi2b-backup-restore", "Backups & Ajustes",
                        "Suas redes de segurança: ponto de restauração do Windows, reversão total e backups de valores."),
                new ResponsiveGrid(320, 3, restoreCard(), revertCard(), preferencesCard()),
                legacyBackupsCard());
        refreshApplied();
        reload();
    }

    private Node restoreCard() {
        ActionButton create = new ActionButton("Criar ponto agora", "primary");
        create.setDisable(!app.isElevatedProcess());
        create.setOnAction(e -> Ui.run(create,
                () -> app.getSafetyService().createRestorePoint(Brand.NAME + " — ponto manual"),
                r -> {
                    if (r != null) NotificationManager.show(r.message(), r.success() ? NotificationManager.Type.SUCCESS : NotificationManager.Type.WARNING);
                }));
        ActionButton open = new ActionButton("Abrir Restauração do Sistema", "default");
        open.setOnAction(e -> Ui.async(() -> {
            app.getSafetyService().openSystemRestore();
            return true;
        }, r -> {}));
        VBox card = Ui.card();
        card.getChildren().addAll(Ui.cardHeader("mdi2s-shield-refresh-outline", "Ponto de restauração",
                        "Volta o Windows inteiro para um momento anterior, caso algo dê errado."),
                Ui.muted("A Otimização Full cria um automaticamente. A Proteção do Sistema é ativada no disco do Windows se estiver desligada."),
                new FlowPane(8, 8, create, open));
        return card;
    }

    private Node revertCard() {
        ActionButton revert = new ActionButton("Reverter tudo", "danger");
        revert.setDisable(!app.isElevatedProcess());
        revert.setOnAction(e -> {
            int count = app.getTweakService().appliedByNextGenCount();
            if (count == 0) {
                NotificationManager.info("Nenhum ajuste para reverter.");
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Restaurar os valores originais de " + count + " ajustes?", ButtonType.CANCEL, ButtonType.OK);
            confirm.setHeaderText("Reverter tudo");
            confirm.initOwner(getScene().getWindow());
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            Ui.run(revert, () -> app.getTweakService().revertAll(null), results -> {
                if (results == null) return;
                long failed = results.stream().filter(r -> !r.success()).count();
                NotificationManager.show(failed == 0 ? "Todos os ajustes foram revertidos. Reinicie o PC para concluir."
                                : failed + " ajustes não puderam ser revertidos.",
                        failed == 0 ? NotificationManager.Type.SUCCESS : NotificationManager.Type.WARNING);
                refreshApplied();
                app.getNavigationManager().invalidate("dashboard", "tweaks", "full-opt", "cs2", "ram");
            });
        });
        appliedLabel.getStyleClass().add("big-number");
        VBox card = Ui.card();
        card.getChildren().addAll(Ui.cardHeader("mdi2u-undo-variant", "Ajustes do " + Brand.NAME,
                        "Valores originais salvos em ~/.nextgen/tweaks/state.json."),
                appliedLabel, revert);
        return card;
    }

    private void refreshApplied() {
        appliedLabel.setText(app.getTweakService().appliedByNextGenCount() + " ativos");
    }

    private Node preferencesCard() {
        ToggleSwitch tray = new ToggleSwitch(app.getSettings().isCloseToTray());
        tray.setOnAction(e -> {
            app.getSettings().setCloseToTray(tray.isSelected());
            app.getSettings().save();
        });
        Label t = new Label("Fechar para a bandeja");
        t.getStyleClass().add("option-title");
        VBox text = new VBox(2, t, Ui.muted("Mantém Game Booster e RAM Guard funcionando com a janela fechada."));
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(12, text, tray);
        row.setAlignment(Pos.CENTER_LEFT);

        ActionButton folder = new ActionButton("Abrir pasta de dados", "default");
        folder.setOnAction(e -> openFolder(Path.of(System.getProperty("user.home"), ".nextgen")));
        VBox card = Ui.card();
        card.getChildren().addAll(Ui.cardHeader("mdi2c-cog-outline", "Preferências", null), row, folder);
        return card;
    }

    private Node legacyBackupsCard() {
        list.setPrefHeight(240);
        list.setPlaceholder(new Label("Nenhum backup antigo."));
        list.setCellFactory(view -> new ListCell<>() {
            protected void updateItem(BackupSnapshot item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(item.getTimestamp()))
                        + "   ·   " + item.getLabel() + "   ·   " + item.getEntries().size() + " valores");
            }
        });
        ActionButton restore = new ActionButton("Restaurar selecionado", "default");
        restore.setDisable(true);
        list.getSelectionModel().selectedItemProperty().addListener((o, a, item) -> restore.setDisable(item == null));
        restore.setOnAction(e -> restoreSelected(restore));
        ActionButton folder = new ActionButton("Abrir pasta", "default");
        folder.setOnAction(e -> openFolder(app.getBackupService().getBackupDir()));
        backupStatus.getStyleClass().add("card-subtitle");

        VBox card = Ui.card();
        card.getChildren().addAll(
                Ui.cardHeader("mdi2a-archive-outline", "Backups de valores (privacidade e versões anteriores)",
                        "Criados pela página Privacidade e pelas versões 1.x do NextGen."),
                backupStatus, list, new FlowPane(8, 8, restore, folder));
        return card;
    }

    private void reload() {
        Ui.async(() -> app.getBackupService().listBackups(), items -> {
            if (items == null) {
                backupStatus.setText("Falha ao consultar backups.");
                return;
            }
            list.getItems().setAll(items);
            backupStatus.setText(items.size() + " backups disponíveis.");
        });
    }

    private void restoreSelected(ActionButton button) {
        BackupSnapshot selected = list.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Restaurar os valores salvos em '" + selected.getLabel() + "'? Isso pode substituir ajustes feitos depois desse backup.",
                ButtonType.CANCEL, ButtonType.OK);
        confirmation.initOwner(getScene().getWindow());
        confirmation.setHeaderText("Restaurar backup");
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        Ui.run(button, () -> app.getBackupService().restoreBackup(selected), ok ->
                NotificationManager.show(Boolean.TRUE.equals(ok) ? "Backup restaurado." : "Restauração incompleta. Verifique as permissões.",
                        Boolean.TRUE.equals(ok) ? NotificationManager.Type.SUCCESS : NotificationManager.Type.WARNING));
    }

    private void openFolder(Path path) {
        try {
            Files.createDirectories(path);
            new ProcessBuilder("explorer.exe", path.toString()).start();
        } catch (Exception ex) {
            NotificationManager.warning("Não foi possível abrir a pasta.");
        }
    }
}
