package com.nextgen.optimizer.ui.pages;

import com.nextgen.optimizer.App;
import com.nextgen.optimizer.ui.components.*;
import com.nextgen.optimizer.core.NotificationManager;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.List;

/**
 * Performance page — Game Mode, power plans, process priority,
 * CPU affinity, RAM cleanup, temp files, startup apps.
 */
public class PerformancePage extends VBox {

    private final App app;
    private boolean gameModeActive = false;
    private Label statusDot;
    private Label statusText;
    private Label offSide;
    private Label onSide;
    private HBox gameModeCardBox;

    public PerformancePage(App app) {
        this.app = app;
        getStyleClass().add("page-container");
        setSpacing(20);
        setPadding(new Insets(20, 16, 28, 16));
        buildUI();
    }

    private void buildUI() {
        // Header
        VBox header = new VBox(4);
        Label title = new Label("Performance & FPS Boost");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Otimização Extrema Unificada: Modo Game, Plano de Energia, Registro, Efeitos e Processos");
        sub.getStyleClass().add("page-subtitle");
        header.getChildren().addAll(title, sub);

        // Game Mode Master Switch
        HBox gameModeCard = buildGameModeCard();

        // FPS Boost Section
        VBox fpsBoostSection = buildFpsBoostSection();

        VBox competitiveLatencySection = buildCompetitiveLatencySection();

        // Power Plan section
        VBox powerPlanSection = buildPowerPlanSection();

        // Process Management
        VBox processSection = buildProcessSection();

        // Cleanup section
        VBox cleanupSection = buildCleanupSection();

        // Startup section
        VBox startupSection = buildStartupSection();

        getChildren().addAll(header, gameModeCard, fpsBoostSection, competitiveLatencySection, powerPlanSection, processSection, cleanupSection, startupSection);
    }

    private HBox buildGameModeCard() {
        gameModeCardBox = new HBox(24);
        gameModeCardBox.getStyleClass().add("card");
        gameModeCardBox.setAlignment(Pos.CENTER_LEFT);
        gameModeCardBox.setPadding(new Insets(22, 26, 22, 26));

        // Left info area
        VBox infoBox = new VBox(8);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        HBox badgeRow = new HBox(8);
        badgeRow.setAlignment(Pos.CENTER_LEFT);
        Label badge = new Label("OTIMIZAÇÃO GERAL EM UM CLIQUE");
        badge.setStyle("-fx-background-color: #3b82f622; -fx-text-fill: #60a5fa; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 4;");

        statusDot = new Label("●");
        statusText = new Label("DESATIVADO");
        badgeRow.getChildren().addAll(badge, statusDot, statusText);

        Label title = new Label("Modo Game Extremo");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label desc = new Label("Aplica o plano Ultimate Performance, libera memória standby em tempo real e prioriza o processo do jogo na CPU.");
        desc.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");
        desc.setWrapText(true);

        infoBox.getChildren().addAll(badgeRow, title, desc);

        // Right side: Sleek Hardware Interruptor (ON / OFF)
        VBox switchContainer = new VBox(8);
        switchContainer.setAlignment(Pos.CENTER_RIGHT);

        Label switchHeader = new Label("STATUS DO MODO GAME");
        switchHeader.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px; -fx-font-weight: bold;");

        offSide = new Label("OFF");
        offSide.setPrefSize(64, 38);
        offSide.setAlignment(Pos.CENTER);

        onSide = new Label("ON");
        onSide.setPrefSize(64, 38);
        onSide.setAlignment(Pos.CENTER);

        HBox pillSwitch = new HBox(offSide, onSide);
        pillSwitch.setStyle("-fx-background-color: #0f172a; -fx-border-color: #334155; -fx-border-radius: 24; -fx-background-radius: 24; -fx-padding: 4; -fx-cursor: hand;");

        pillSwitch.setOnMouseClicked(e -> toggleGameMode());

        switchContainer.getChildren().addAll(switchHeader, pillSwitch);

        // Check current state
        try {
            gameModeActive = app.getPerformanceService().isGameModeActive();
        } catch (Exception ignored) {}
        updateGameModeVisuals();

        gameModeCardBox.getChildren().addAll(infoBox, switchContainer);
        return gameModeCardBox;
    }

    private void updateGameModeVisuals() {
        if (gameModeActive) {
            offSide.setStyle("-fx-text-fill: #64748b; -fx-font-weight: bold; -fx-font-size: 14px; -fx-background-color: transparent;");
            onSide.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-background-radius: 20; -fx-effect: dropshadow(three-pass-box, rgba(16,185,129,0.4), 12, 0, 0, 0);");
            statusDot.setStyle("-fx-text-fill: #10b981; -fx-font-size: 15px;");
            statusText.setText("ATIVADO");
            statusText.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold; -fx-font-size: 13px;");
            gameModeCardBox.setStyle("-fx-background-color: linear-gradient(to right, #064e3b26, #0f172a); -fx-border-color: #10b98177; -fx-border-radius: 14; -fx-background-radius: 14; -fx-padding: 22 26 22 26;");
        } else {
            offSide.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-background-radius: 20;");
            onSide.setStyle("-fx-text-fill: #64748b; -fx-font-weight: bold; -fx-font-size: 14px; -fx-background-color: transparent;");
            statusDot.setStyle("-fx-text-fill: #64748b; -fx-font-size: 15px;");
            statusText.setText("DESATIVADO");
            statusText.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: bold; -fx-font-size: 13px;");
            gameModeCardBox.setStyle("-fx-background-color: linear-gradient(to right, #111827, #1e293b); -fx-border-color: #3b82f633; -fx-border-radius: 14; -fx-background-radius: 14; -fx-padding: 22 26 22 26;");
        }
    }

    private void toggleGameMode() {
        new Thread(() -> {
            try {
                if (gameModeActive) {
                    app.getPerformanceService().deactivateGameMode().join();
                    gameModeActive = false;
                    Platform.runLater(() -> {
                        updateGameModeVisuals();
                        NotificationManager.show("Modo Game desativado", NotificationManager.Type.INFO);
                    });
                } else {
                    app.getPerformanceService().activateGameMode().join();
                    boolean fpsOk = app.getFpsBoostService().applyAll();
                    app.getRamService().clearStandbyMemory();
                    gameModeActive = true;
                    Platform.runLater(() -> {
                        updateGameModeVisuals();
                        NotificationManager.show(fpsOk ? "Modo Game & FPS Boost ativados!" : "Modo Game ativado; FPS Boost parcial. Verifique permissões.",
                                fpsOk ? NotificationManager.Type.SUCCESS : NotificationManager.Type.WARNING);
                    });
                }
            } catch (Exception ex) {
                Platform.runLater(() ->
                    NotificationManager.show("Erro: " + ex.getMessage(), NotificationManager.Type.ERROR));
            }
        }).start();
    }

    private VBox buildFpsBoostSection() {
        VBox section = new VBox(12);
        Label sectionTitle = new Label("Otimizações de Registro & FPS Boost");
        sectionTitle.getStyleClass().add("section-title");

        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);

        ActionButton applyAll = new ActionButton("🚀 Aplicar Todas Otimizações de FPS", "primary");
        applyAll.setOnAction(e -> {
            applyAll.setLoading(true);
            new Thread(() -> {
                boolean success = app.getFpsBoostService().applyAll();
                Platform.runLater(() -> {
                    applyAll.setLoading(false);
                    NotificationManager.show(success ? "Todas as otimizações de FPS aplicadas!" : "Otimizações aplicadas parcialmente. Verifique permissões.",
                            success ? NotificationManager.Type.SUCCESS : NotificationManager.Type.WARNING);
                });
            }).start();
        });

        ActionButton restoreAll = new ActionButton("↩️ Restaurar Padrões", "danger");
        restoreAll.setOnAction(e -> {
            restoreAll.setLoading(true);
            new Thread(() -> {
                app.getFpsBoostService().restoreAll();
                Platform.runLater(() -> {
                    restoreAll.setLoading(false);
                    NotificationManager.show("Configurações do Windows restauradas", NotificationManager.Type.INFO);
                });
            }).start();
        });

        topBar.getChildren().addAll(applyAll, restoreAll);

        VBox rows = new VBox(8);
        rows.getChildren().addAll(
            createFpsRow("🎮", "Xbox Game Bar", "Desativa a barra de jogos em segundo plano",
                () -> app.getFpsBoostService().isGameBarDisabled(),
                (on) -> app.getFpsBoostService().setGameBarDisabled(on)),

            createFpsRow("📹", "Game DVR & Gravação", "Desativa gravação contínua de tela do Windows",
                () -> app.getFpsBoostService().isGameDvrDisabled(),
                (on) -> app.getFpsBoostService().setGameDvrDisabled(on)),

            createFpsRow("✨", "Efeitos Visuais & Transparência", "Desativa efeitos de vidro/acrílico pesados da GPU",
                () -> app.getFpsBoostService().isTransparencyDisabled(),
                (on) -> app.getFpsBoostService().setTransparencyDisabled(on)),

            createFpsRow("🖱️", "Aceleração de Mouse (Precisão 1:1)", "Desativa Precisão do Ponteiro do Windows para pontaria crua",
                () -> app.getFpsBoostService().isMouseAccelerationDisabled(),
                (on) -> app.getFpsBoostService().setMouseAccelerationDisabled(on))
        );

        section.getChildren().addAll(sectionTitle, topBar, rows);
        return section;
    }

    private VBox buildCompetitiveLatencySection() {
        VBox section = new VBox(12);
        section.getStyleClass().add("card");
        section.setPadding(new Insets(18, 18, 18, 18));

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox text = new VBox(3);
        HBox.setHgrow(text, Priority.ALWAYS);

        Label eyebrow = new Label("PACOTE AVANÇADO");
        eyebrow.getStyleClass().add("latency-eyebrow");

        Label title = new Label("FPS Competitivo & Baixa Latência");
        title.getStyleClass().add("section-title");

        Label desc = new Label("Ajusta HAGS, MPO, fullscreen/GameDVR e prioridade multimídia de jogos. Pode exigir reinício para surtir efeito total.");
        desc.getStyleClass().addAll("text-secondary", "font-sm");
        desc.setWrapText(true);
        text.getChildren().addAll(eyebrow, title, desc);

        Label state = new Label(app.getFpsBoostService().isCompetitiveLatencyPackApplied() ? "APLICADO" : "PENDENTE");
        state.getStyleClass().add(app.getFpsBoostService().isCompetitiveLatencyPackApplied() ? "status-pill-on" : "status-pill-warn");

        header.getChildren().addAll(text, state);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        grid.getColumnConstraints().addAll(col1, col2);

        grid.add(createLatencyStatus("HAGS", "Hardware GPU Scheduling", app.getFpsBoostService().isHardwareGpuSchedulingEnabled()), 0, 0);
        grid.add(createLatencyStatus("MPO", "Desativa overlays problemáticos do DWM", app.getFpsBoostService().isMpoDisabled()), 1, 0);
        grid.add(createLatencyStatus("FSE", "Fullscreen/GameDVR otimizado", app.getFpsBoostService().isGameDvrFullscreenOptimized()), 0, 1);
        grid.add(createLatencyStatus("MMCSS", "Prioridade alta para tarefa Games", app.getFpsBoostService().isMultimediaGameProfileOptimized()), 1, 1);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);

        ActionButton apply = new ActionButton("Aplicar pacote competitivo", "primary");
        apply.setOnAction(e -> {
            apply.setLoading(true);
            new Thread(() -> {
                boolean success = app.getFpsBoostService().applyCompetitiveLatencyPack();
                Platform.runLater(() -> {
                    apply.setLoading(false);
                    state.getStyleClass().setAll(success ? "status-pill-on" : "status-pill-warn");
                    state.setText(success ? "APLICADO" : "PARCIAL");
                    NotificationManager.show(
                            success ? "Pacote competitivo aplicado. Reinicie o PC para efeito total." : "Pacote aplicado parcialmente. Rode como admin e tente novamente.",
                            success ? NotificationManager.Type.SUCCESS : NotificationManager.Type.WARNING);
                });
            }).start();
        });

        ActionButton restore = new ActionButton("Restaurar pacote", "default");
        restore.setOnAction(e -> {
            restore.setLoading(true);
            new Thread(() -> {
                app.getFpsBoostService().restoreCompetitiveLatencyPack();
                Platform.runLater(() -> {
                    restore.setLoading(false);
                    state.getStyleClass().setAll("status-pill-warn");
                    state.setText("RESTAURADO");
                    NotificationManager.show("Pacote competitivo restaurado", NotificationManager.Type.INFO);
                });
            }).start();
        });

        actions.getChildren().addAll(apply, restore);
        section.getChildren().addAll(header, grid, actions);
        return section;
    }

    private HBox createLatencyStatus(String tag, String text, boolean active) {
        HBox row = new HBox(10);
        row.getStyleClass().add("latency-status-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinHeight(56);

        Label badge = new Label(tag);
        badge.getStyleClass().add(active ? "latency-tag-on" : "latency-tag-off");

        VBox copy = new VBox(2);
        HBox.setHgrow(copy, Priority.ALWAYS);
        Label name = new Label(text);
        name.getStyleClass().addAll("text-primary", "font-sm", "font-semibold");
        Label status = new Label(active ? "Ativo" : "Pendente");
        status.getStyleClass().add(active ? "text-success" : "text-muted");
        copy.getChildren().addAll(name, status);

        row.getChildren().addAll(badge, copy);
        return row;
    }

    private HBox createFpsRow(String icon, String name, String desc, java.util.function.BooleanSupplier checker, java.util.function.Consumer<Boolean> action) {
        HBox row = new HBox(12);
        row.getStyleClass().add("card");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 16, 12, 16));

        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 20px;");

        VBox textBox = new VBox(2);
        HBox.setHgrow(textBox, Priority.ALWAYS);
        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: white;");
        Label descLbl = new Label(desc);
        descLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        textBox.getChildren().addAll(nameLbl, descLbl);

        boolean initial = false;
        try {
            initial = checker.getAsBoolean();
        } catch (Exception ignored) {}

        ToggleSwitch toggle = new ToggleSwitch(initial);
        toggle.setOnAction(e -> {
            boolean newState = toggle.isSelected();
            new Thread(() -> {
                try {
                    action.accept(newState);
                    Platform.runLater(() -> NotificationManager.show(name + ": atualizado!", NotificationManager.Type.SUCCESS));
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        toggle.setSelected(!newState);
                        NotificationManager.show("Erro: " + ex.getMessage(), NotificationManager.Type.ERROR);
                    });
                }
            }).start();
        });

        row.getChildren().addAll(iconLbl, textBox, toggle);
        return row;
    }

    private VBox buildPowerPlanSection() {
        VBox section = new VBox(12);

        Label sectionTitle = new Label("Plano de Energia");
        sectionTitle.getStyleClass().add("section-title");

        HBox buttons = new HBox(10);

        ActionButton highPerf = new ActionButton("Alto Desempenho", "default");
        highPerf.setOnAction(e -> {
            new Thread(() -> {
                app.getPerformanceService().setHighPerformancePlan();
                Platform.runLater(() -> NotificationManager.show("Plano Alto Desempenho ativado", NotificationManager.Type.SUCCESS));
            }).start();
        });

        ActionButton ultimatePerf = new ActionButton("Ultimate Performance", "primary");
        ultimatePerf.setOnAction(e -> {
            new Thread(() -> {
                app.getPerformanceService().setUltimatePerformancePlan();
                Platform.runLater(() -> NotificationManager.show("Plano Ultimate Performance ativado", NotificationManager.Type.SUCCESS));
            }).start();
        });

        // Show current plan
        Label currentPlan = new Label("Plano atual: carregando...");
        currentPlan.getStyleClass().addAll("font-sm", "text-secondary");

        new Thread(() -> {
            String plan = app.getPerformanceService().getActivePowerPlan();
            Platform.runLater(() -> currentPlan.setText("Plano atual: " + plan));
        }).start();

        buttons.getChildren().addAll(highPerf, ultimatePerf);
        section.getChildren().addAll(sectionTitle, currentPlan, buttons);

        return section;
    }

    private VBox buildProcessSection() {
        VBox section = new VBox(12);

        Label sectionTitle = new Label("Gerenciamento de Processos");
        sectionTitle.getStyleClass().add("section-title");

        // Process priority
        HBox priorityRow = new HBox(12);
        priorityRow.setAlignment(Pos.CENTER_LEFT);

        Label priorityLabel = new Label("Definir prioridade alta para:");
        priorityLabel.getStyleClass().addAll("text-primary", "font-sm");

        ComboBox<String> processCombo = new ComboBox<>();
        processCombo.setPromptText("Selecione o processo");
        processCombo.setPrefWidth(250);

        ActionButton setPriority = new ActionButton("Prioridade Alta", "primary");
        setPriority.setOnAction(e -> {
            String selected = processCombo.getValue();
            if (selected != null && !selected.isEmpty()) {
                new Thread(() -> {
                    app.getPerformanceService().setProcessPriority(selected, 128);
                    Platform.runLater(() -> NotificationManager.show("Prioridade alta definida para " + selected, NotificationManager.Type.SUCCESS));
                }).start();
            }
        });

        Runnable loadProcesses = () -> {
            new Thread(() -> {
                List<String[]> processes = app.getPerformanceService().getRunningProcesses();
                Platform.runLater(() -> {
                    processCombo.getItems().clear();
                    for (String[] p : processes) {
                        processCombo.getItems().add(p[0]);
                    }
                });
            }).start();
        };

        ActionButton refreshBtn = new ActionButton("🔄 Atualizar", "default");
        refreshBtn.setOnAction(e -> loadProcesses.run());

        loadProcesses.run();

        priorityRow.getChildren().addAll(priorityLabel, processCombo, setPriority, refreshBtn);

        // Kill background processes
        HBox killRow = new HBox(12);
        killRow.setAlignment(Pos.CENTER_LEFT);

        ActionButton killBgBtn = new ActionButton("Encerrar Programas em Segundo Plano", "danger");
        killBgBtn.setOnAction(e -> {
            new Thread(() -> {
                app.getPerformanceService().killBackgroundProcesses(List.of(
                    "GameBarPresenceWriter.exe", "XboxGameBar.exe", "XboxPcApp.exe",
                    "YourPhone.exe", "PhoneExperienceHost.exe", "Widgets.exe",
                    "MicrosoftEdgeUpdate.exe", "AdobeARM.exe", "AdobeCollabSync.exe"
                ));
                Platform.runLater(() -> NotificationManager.show("Processos em segundo plano encerrados", NotificationManager.Type.SUCCESS));
            }).start();
        });

        ActionButton restartExplorer = new ActionButton("🔄 Reiniciar Explorer", "warning");
        restartExplorer.setOnAction(e -> {
            new Thread(() -> {
                app.getPerformanceService().restartExplorer();
                Platform.runLater(() -> NotificationManager.show("Explorer reiniciado", NotificationManager.Type.INFO));
            }).start();
        });

        killRow.getChildren().addAll(killBgBtn, restartExplorer);

        section.getChildren().addAll(sectionTitle, priorityRow, killRow);
        return section;
    }

    private VBox buildCleanupSection() {
        VBox section = new VBox(12);

        Label sectionTitle = new Label("Limpeza");
        sectionTitle.getStyleClass().add("section-title");

        HBox buttons = new HBox(12);

        ActionButton clearRam = new ActionButton("🧹 Limpar RAM Standby", "default");
        clearRam.setOnAction(e -> {
            new Thread(() -> {
                app.getPerformanceService().clearStandbyRam();
                Platform.runLater(() -> NotificationManager.show("RAM Standby limpa!", NotificationManager.Type.SUCCESS));
            }).start();
        });

        ActionButton clearTemp = new ActionButton("🗑️ Limpar Arquivos Temporários", "default");
        clearTemp.setOnAction(e -> {
            clearTemp.setLoading(true);
            new Thread(() -> {
                long freed = app.getPerformanceService().clearTempFiles();
                String size = formatBytes(freed);
                Platform.runLater(() -> {
                    clearTemp.setLoading(false);
                    NotificationManager.show(size + " liberados!", NotificationManager.Type.SUCCESS);
                });
            }).start();
        });

        ActionButton quickOptBtn = new ActionButton("⚡ Otimizações Rápidas", "primary");
        quickOptBtn.setOnAction(e -> app.getNavigationManager().navigateTo("quick-opt"));

        buttons.getChildren().addAll(quickOptBtn, clearRam, clearTemp);
        section.getChildren().addAll(sectionTitle, buttons);

        return section;
    }

    private VBox buildStartupSection() {
        VBox section = new VBox(12);

        Label sectionTitle = new Label("Aplicativos de Inicialização");
        sectionTitle.getStyleClass().add("section-title");

        Label desc = new Label("Gerencie quais aplicativos iniciam com o Windows");
        desc.getStyleClass().addAll("text-secondary", "font-sm");

        VBox startupList = new VBox(6);

        // Load startup apps
        new Thread(() -> {
            List<String> apps = app.getPerformanceService().getStartupApps();
            Platform.runLater(() -> {
                if (apps.isEmpty()) {
                    Label empty = new Label("Nenhum aplicativo de inicialização encontrado");
                    empty.getStyleClass().addAll("text-muted", "font-sm");
                    startupList.getChildren().add(empty);
                } else {
                    for (String appName : apps) {
                        HBox row = new HBox(12);
                        row.getStyleClass().add("opt-row");
                        row.setAlignment(Pos.CENTER_LEFT);

                        Label name = new Label(appName);
                        name.getStyleClass().add("opt-row-label");
                        HBox.setHgrow(name, Priority.ALWAYS);

                        boolean isEnabled = app.getPerformanceService().isStartupAppEnabled(appName);
                        ToggleSwitch toggle = new ToggleSwitch(isEnabled);
                        toggle.setOnAction(ev -> {
                            boolean active = toggle.isSelected();
                            app.getPerformanceService().setStartupAppEnabled(appName, active);
                            NotificationManager.show(appName + (active ? " ativado na inicialização" : " desativado da inicialização"), NotificationManager.Type.INFO);
                        });

                        row.getChildren().addAll(name, toggle);
                        startupList.getChildren().add(row);
                    }
                }
            });
        }).start();

        section.getChildren().addAll(sectionTitle, desc, startupList);
        return section;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
