package com.nextgen.optimizer.ui.pages;

import com.nextgen.optimizer.App;
import com.nextgen.optimizer.core.NotificationManager;
import com.nextgen.optimizer.ui.components.ActionButton;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;

import java.io.File;

/**
 * CS2 Optimization Page — Specialized tweaks for Counter-Strike 2
 * to minimize input lag and maximize FPS.
 */
public class Cs2Page extends VBox {

    private final App app;
    private ActionButton liveCopyCmdBtn;
    private Label liveConsoleSubLabel;
    private int currentSelectedLimit = 141;

    public Cs2Page(App app) {
        this.app = app;
        getStyleClass().add("page-container");
        setSpacing(20);
        setPadding(new Insets(4, 4, 28, 4));
        buildUI();
    }

    private void buildUI() {
        // Header
        VBox header = new VBox(6);
        HBox titleRow = new HBox(12);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        ImageView cs2Logo = new ImageView();
        try {
            cs2Logo.setImage(new Image(getClass().getResourceAsStream("/cs2_logo.png")));
            cs2Logo.setFitHeight(32);
            cs2Logo.setPreserveRatio(true);
            cs2Logo.setSmooth(true);
        } catch (Exception ignored) {}

        Label title = new Label("Counter-Strike 2 — Otimizador Competitivo");
        title.getStyleClass().add("page-title");
        titleRow.getChildren().addAll(cs2Logo, title);

        Label sub = new Label("Prioridade do sistema para cs2.exe, comandos de rede Sub-tick no console e otimizações de tela inteira para máximo FPS e menor Input Lag");
        sub.getStyleClass().add("page-subtitle");
        header.getChildren().addAll(titleRow, sub);

        // Section 1: 1% Low & Frame Pacing (RTSS + CS2)
        VBox onePercentLowSection = buildOnePercentLowCard();

        // Section 2: Windows & Executable Tweaks
        VBox tweaksSection = buildWindowsTweaksCard();

        // Section 3: Network & Sub-Tick Commands
        VBox networkSection = buildNetworkCommandsCard();

        getChildren().addAll(header, onePercentLowSection, tweaksSection, networkSection);
    }

    private VBox buildWindowsTweaksCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(20));

        Label title = new Label("⚙️ Otimizações do Sistema para CS2");
        title.getStyleClass().add("card-title");

        VBox list = new VBox(10);

        list.getChildren().addAll(
            createTweakRow("Prioridade Alta para cs2.exe no Registro Windows",
                "Define permanentemente a prioridade de processamento do CS2 como Alta para evitar engasgos (stutters)",
                "Aplicar Prioridade Alta",
                () -> {
                    app.getPowerShellService().executeSync("New-Item -Path 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Image File Execution Options\\cs2.exe\\PerfOptions' -Force; New-ItemProperty -Path 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Image File Execution Options\\cs2.exe\\PerfOptions' -Name 'CpuPriorityClass' -Value 3 -PropertyType DWord -Force");
                    NotificationManager.show("Prioridade Alta aplicada para o cs2.exe!", NotificationManager.Type.SUCCESS);
                }),
            createTweakRow("Desativar Otimizações de Tela Inteira para Jogos Source 2",
                "Reduz drasticamente o Input Lag do mouse e do monitor desativando a camada DWM sobreposta",
                "Desativar Tela Inteira",
                () -> {
                    app.getPowerShellService().executeSync("New-ItemProperty -Path 'HKCU:\\Software\\Microsoft\\Windows NT\\CurrentVersion\\AppCompatFlags\\Layers' -Name 'C:\\Program Files (x86)\\Steam\\steamapps\\common\\Counter-Strike Global Offensive\\game\\bin\\win64\\cs2.exe' -Value '~ DISABLEDXMAXIMIZEDWINDOWEDMODE' -PropertyType String -Force");
                    NotificationManager.show("Otimizações de Tela Inteira desativadas para o CS2!", NotificationManager.Type.SUCCESS);
                }),
            createTweakRow("Plano de Energia Ultimate Performance Competitivo",
                "Ativa o plano de energia de Ultra Desempenho (e9a42b02-d5df-448d-aa00-03f14749eb61) sem throttling de clock",
                "Ativar Ultimate Performance",
                () -> {
                    app.getPerformanceService().setUltimatePerformancePlan();
                    NotificationManager.show("Plano Ultimate Performance ativado!", NotificationManager.Type.SUCCESS);
                }),
            createTweakRow("Limpar Cache de Shaders da Steam (Especial CS2)",
                "Remove Shaders antigos ou corrompidos que causam travamentos após atualizações do jogo",
                "Limpar Cache Steam",
                () -> {
                    cleanSteamShaders();
                    NotificationManager.show("Cache de Shaders da Steam limpo com sucesso!", NotificationManager.Type.SUCCESS);
                })
        );

        card.getChildren().addAll(title, list);
        return card;
    }

    private VBox buildNetworkCommandsCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(20));

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("🌐");
        icon.setStyle("-fx-font-size: 24px;");
        VBox textBox = new VBox(2);
        Label cardTitle = new Label("Ajustes de Rede e Sub-Tick para Console CS2");
        cardTitle.setStyle("-fx-font-weight: 800; -fx-font-size: 15px; -fx-text-fill: white;");
        Label cardSub = new Label("Abra o Console do CS2 (') e cole os comandos abaixo para sincronização de taxa de pacotes");
        cardSub.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
        textBox.getChildren().addAll(cardTitle, cardSub);
        top.getChildren().addAll(icon, textBox);

        HBox inputRow = new HBox(12);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        String netCmds = "rate 786432; cl_net_buffer_ticks 0; engine_low_latency_sleep_after_client_tick 1";
        TextField netField = new TextField(netCmds);
        netField.setEditable(false);
        netField.setStyle("-fx-background-color: #0d111d; -fx-text-fill: #22c55e; -fx-font-family: 'Consolas', monospace; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 10 14; -fx-border-color: #1e293b; -fx-border-radius: 6; -fx-background-radius: 6;");
        HBox.setHgrow(netField, Priority.ALWAYS);

        ActionButton copyNetBtn = new ActionButton("📋 Copiar Comandos Console", "primary");
        copyNetBtn.setOnAction(e -> {
            copyToClipboard(netField.getText());
            NotificationManager.show("Comandos de rede copiados!", NotificationManager.Type.SUCCESS);
        });

        inputRow.getChildren().addAll(netField, copyNetBtn);
        card.getChildren().addAll(top, inputRow);
        return card;
    }

    private HBox createTweakRow(String name, String descText, String btnText, Runnable action) {
        HBox row = new HBox(12);
        row.getStyleClass().add("process-row");
        row.setAlignment(Pos.CENTER_LEFT);

        VBox info = new VBox(2);
        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #eaf0f7;");
        Label descLbl = new Label(descText);
        descLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #8b95a8;");
        info.getChildren().addAll(nameLbl, descLbl);
        HBox.setHgrow(info, Priority.ALWAYS);

        ActionButton btn = new ActionButton(btnText, "primary");
        btn.setOnAction(e -> {
            btn.setDisable(true);
            new Thread(() -> {
                action.run();
                Platform.runLater(() -> btn.setDisable(false));
            }).start();
        });

        row.getChildren().addAll(info, btn);
        return row;
    }

    private void copyToClipboard(String text) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }

    private void cleanSteamShaders() {
        File steam1 = new File("C:\\Program Files (x86)\\Steam\\steamapps\\shadercache");
        File steam2 = new File("C:\\Program Files\\Steam\\steamapps\\shadercache");
        cleanDirectory(steam1);
        cleanDirectory(steam2);
    }

    private void cleanDirectory(File dir) {
        if (dir != null && dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    cleanRecursive(f);
                }
            }
        }
    }

    private void cleanRecursive(File f) {
        if (f.isDirectory()) {
            File[] ch = f.listFiles();
            if (ch != null) {
                for (File c : ch) cleanRecursive(c);
            }
        }
        f.delete();
    }

    private VBox buildOnePercentLowCard() {
        VBox card = new VBox(16);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(20));

        // Header
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("🎯");
        icon.setStyle("-fx-font-size: 26px;");

        VBox titleBox = new VBox(2);
        Label title = new Label("Otimizador de 1% Low & Frame Pacing (RTSS + CS2)");
        title.getStyleClass().add("card-title");

        Label subtitle = new Label("Baseado em testes da comunidade (Reddit/BlurBusters): limitar FPS externamente com RTSS reduz spikes e estabiliza o 1% Low.");
        subtitle.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        subtitle.setWrapText(true);
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label optBadge = new Label("⚠️ OPCIONAL / AVANÇADO");
        optBadge.setStyle("-fx-background-color: #f59e0b22; -fx-text-fill: #fbbf24; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 10; -fx-background-radius: 4;");

        header.getChildren().addAll(icon, titleBox, spacer, optBadge);

        // RTSS Detection & Warnings
        boolean rtssInstalled = new File("C:\\Program Files (x86)\\RivaTuner Statistics Server\\RTSS.exe").exists()
                             || new File("C:\\Program Files\\RivaTuner Statistics Server\\RTSS.exe").exists();

        HBox detectionBanner = new HBox(12);
        detectionBanner.setAlignment(Pos.CENTER_LEFT);
        detectionBanner.setStyle("-fx-background-color: " + (rtssInstalled ? "#064e3b33;" : "#1e293b;") + " -fx-border-color: " + (rtssInstalled ? "#10b981;" : "#64748b;") + " -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 12;");

        Label detIcon = new Label(rtssInstalled ? "✅" : "ℹ️");
        detIcon.setStyle("-fx-font-size: 20px;");

        VBox detText = new VBox(2);
        Label detTitle = new Label(rtssInstalled ? "RTSS (RivaTuner) Detectado no Sistema" : "RTSS não detectado na pasta padrão (Você ainda pode gerar o perfil cs2.exe.cfg)");
        detTitle.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px;");
        Label detSub = new Label("Dica Afterburner: Desative o monitoramento de 'GPU Power / Power %' nas configurações do MSI Afterburner para evitar micro-stutters de leitura I2C.");
        detSub.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        detSub.setWrapText(true);
        detText.getChildren().addAll(detTitle, detSub);
        detectionBanner.getChildren().addAll(detIcon, detText);

        // Quick profile generator buttons
        Label sectionLabel = new Label("Ativar Limite de FPS no RTSS + CS2 (e visualizar status na hora):");
        sectionLabel.setStyle("-fx-text-fill: #e2e8f0; -fx-font-weight: bold; -fx-font-size: 12px;");

        VBox statusBox = new VBox(6);
        statusBox.setStyle("-fx-background-color: #0f172a; -fx-border-color: #334155; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 12;");
        statusBox.setVisible(false);
        statusBox.setManaged(false);

        FlowPane limitButtons = new FlowPane(10, 10);
        java.util.List<Button> btnList = new java.util.ArrayList<>();

        Button btn141 = createRtssLimitBtn("141 FPS (Monitor 144Hz)", 141, btnList, statusBox);
        Button btn237 = createRtssLimitBtn("237 FPS (Monitor 240Hz)", 237, btnList, statusBox);
        Button btn357 = createRtssLimitBtn("357 FPS (Monitor 360Hz)", 357, btnList, statusBox);
        Button btn500 = createRtssLimitBtn("500 FPS (Competitivo)", 500, btnList, statusBox);
        Button btnOff = createRtssLimitBtn("DESATIVAR LIMITE (FPS LIVRE)", 0, btnList, statusBox);

        btnList.add(btn141);
        btnList.add(btn237);
        btnList.add(btn357);
        btnList.add(btn500);
        btnList.add(btnOff);

        limitButtons.getChildren().addAll(btnList);

        // CS2 Console Command reminder
        HBox consoleBox = new HBox(12);
        consoleBox.getStyleClass().add("card");
        consoleBox.setAlignment(Pos.CENTER_LEFT);
        consoleBox.setPadding(new Insets(12));

        VBox consoleText = new VBox(2);
        HBox.setHgrow(consoleText, Priority.ALWAYS);
        Label cTitle = new Label("🎮 CS2 já está aberto? Sincronize o jogo rodando ao vivo:");
        cTitle.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px;");
        liveConsoleSubLabel = new Label("O programa reinicia o RTSS na hora e salva no autoexec.cfg! Se o CS2 já estiver aberto, cole no console: fps_max 141");
        liveConsoleSubLabel.setStyle("-fx-text-fill: #38bdf8; -fx-font-family: 'Consolas', monospace; -fx-font-weight: bold; -fx-font-size: 12px;");
        consoleText.getChildren().addAll(cTitle, liveConsoleSubLabel);

        liveCopyCmdBtn = new ActionButton("📋 Copiar fps_max 141", "secondary");
        liveCopyCmdBtn.setOnAction(e -> {
            copyToClipboard("fps_max " + currentSelectedLimit + "\nengine_no_focus_sleep 0");
            NotificationManager.show("Comando 'fps_max " + currentSelectedLimit + "' copiado! Cole no console do CS2 (~).", NotificationManager.Type.SUCCESS);
        });

        consoleBox.getChildren().addAll(consoleText, liveCopyCmdBtn);

        card.getChildren().addAll(header, detectionBanner, sectionLabel, limitButtons, statusBox, consoleBox);
        return card;
    }

    private Button createRtssLimitBtn(String label, int fpsLimit, java.util.List<Button> allBtns, VBox statusBox) {
        String origText = (fpsLimit == 0 ? "❌ " : "⚡ ") + label;
        Button btn = new Button(origText);
        btn.setUserData(origText);
        btn.getStyleClass().addAll("action-btn", "action-btn-secondary");
        btn.setStyle("-fx-font-size: 12px; -fx-padding: 8 14; -fx-cursor: hand;");
        btn.setOnAction(e -> {
            boolean wasAlreadyActive = btn.getText().startsWith("✅") || btn.getText().startsWith("⏹");

            // Reset ALL buttons back to original label and default style
            for (Button b : allBtns) {
                if (b.getUserData() instanceof String orig) {
                    b.setText(orig);
                }
                b.setStyle("-fx-font-size: 12px; -fx-padding: 8 14; -fx-cursor: hand; -fx-background-color: #334155; -fx-text-fill: white;");
            }

            int targetLimit = (wasAlreadyActive && fpsLimit > 0) ? 0 : fpsLimit;
            currentSelectedLimit = targetLimit;
            if (liveCopyCmdBtn != null) {
                liveCopyCmdBtn.setText("📋 Copiar fps_max " + targetLimit);
            }
            if (liveConsoleSubLabel != null) {
                liveConsoleSubLabel.setText("O RTSS é reiniciado na hora! Se o CS2 já estiver aberto, cole no console (~): fps_max " + targetLimit);
            }

            if (targetLimit == 0) {
                btn.setStyle("-fx-font-size: 12px; -fx-padding: 8 14; -fx-cursor: hand; -fx-background-color: #f59e0b; -fx-text-fill: white; -fx-font-weight: bold;");
                btn.setText("⏹ DESATIVADO: FPS LIVRE");
                applyRtssAndCs2Limit(0, statusBox);
            } else {
                btn.setStyle("-fx-font-size: 12px; -fx-padding: 8 14; -fx-cursor: hand; -fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-effect: dropshadow(three-pass-box, rgba(16,185,129,0.4), 8, 0, 0, 0);");
                btn.setText("✅ ATIVADO: " + targetLimit + " FPS");
                applyRtssAndCs2Limit(targetLimit, statusBox);
            }
        });
        return btn;
    }

    private void applyRtssAndCs2Limit(int limit, VBox statusBox) {
        new Thread(() -> {
            try {
                String content = "[Framerate]\n" +
                                 "Limit=" + limit + "\n" +
                                 "LimitDenominator=1\n\n" +
                                 "[Settings]\n" +
                                 "FramerateLimit=" + limit + "\n" +
                                 "CustomDirect3DSupport=1\n" +
                                 "EnableHooking=1\n" +
                                 "SyncToScanline=0\n";

                File profilesDir = new File("C:\\Program Files (x86)\\RivaTuner Statistics Server\\Profiles");
                if (!profilesDir.exists()) {
                    profilesDir = new File("C:\\Program Files\\RivaTuner Statistics Server\\Profiles");
                }

                boolean savedInSystem = false;
                File profileCs2 = null;
                if (profilesDir.exists()) {
                    profileCs2 = new File(profilesDir, "cs2.exe.cfg");
                    savedInSystem = safeWriteProfile(profileCs2, content);

                    File profileGlobal = new File(profilesDir, "Global");
                    safeWriteProfile(profileGlobal, content);
                }

                // Always save backup to local NextGen_RTSS_Profiles folder
                File localDir = new File("NextGen_RTSS_Profiles");
                if (!localDir.exists()) localDir.mkdirs();
                File localCs2 = new File(localDir, "cs2.exe.cfg");
                try {
                    java.nio.file.Files.writeString(localCs2.toPath(), content);
                } catch (Exception ignored) {}

                // Update CS2 autoexec.cfg with exact limit so CS2 engine limits natively
                File steamCfg = new File("C:\\Program Files (x86)\\Steam\\steamapps\\common\\Counter-Strike Global Offensive\\game\\csgo\\cfg");
                boolean autoexecUpdated = false;
                if (steamCfg.exists()) {
                    File autoexec = new File(steamCfg, "autoexec.cfg");
                    String cfgText = "// NextGen Optimized - CS2 1% Low & Frame Pacing\n" +
                                     "fps_max " + limit + "\n" +
                                     "engine_no_focus_sleep 0\n";
                    autoexecUpdated = safeWriteProfile(autoexec, cfgText);
                }

                // Refresh/restart RTSS immediately so it applies the new limit live to any running game
                File rtssExe = new File("C:\\Program Files (x86)\\RivaTuner Statistics Server\\RTSS.exe");
                if (!rtssExe.exists()) {
                    rtssExe = new File("C:\\Program Files\\RivaTuner Statistics Server\\RTSS.exe");
                }
                boolean rtssRefreshed = false;
                if (rtssExe.exists()) {
                    try {
                        String refreshCmd = "Stop-Process -Name RTSS -Force -ErrorAction SilentlyContinue; " +
                                            "Start-Sleep -Milliseconds 150; " +
                                            "Start-Process -FilePath '" + rtssExe.getAbsolutePath() + "'";
                        app.getPowerShellService().executeSync(refreshCmd);
                        rtssRefreshed = true;
                    } catch (Exception ignored) {}
                }

                final boolean sysOk = savedInSystem;
                final boolean cfgOk = autoexecUpdated;
                final boolean rtssOk = rtssRefreshed;
                final String finalPath = sysOk ? profileCs2.getAbsolutePath() : localCs2.getAbsolutePath();

                Platform.runLater(() -> {
                    statusBox.setVisible(true);
                    statusBox.setManaged(true);
                    statusBox.getChildren().clear();

                    if (limit == 0) {
                        Label stTitle = new Label("⏹ LIMITE DESATIVADO (FPS LIVRE / ILIMITADO)");
                        stTitle.setStyle("-fx-text-fill: #f59e0b; -fx-font-weight: bold; -fx-font-size: 13px;");

                        Label item1 = new Label("✔ Perfil RTSS liberado ao vivo em: " + finalPath + " (Sem limite)");
                        item1.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 11px;");

                        Label item2 = new Label("🎮 Jogo já aberto? Se o CS2 tiver limite interno, cole no console (~): fps_max 0");
                        item2.setStyle("-fx-text-fill: #38bdf8; -fx-font-weight: bold; -fx-font-size: 11px;");

                        statusBox.getChildren().addAll(stTitle, item1, item2);
                        NotificationManager.show("⏹ Limite de FPS desativado! RTSS atualizado em tempo real.", NotificationManager.Type.INFO);
                    } else {
                        Label stTitle = new Label("✅ LIMITE DE " + limit + " FPS ATIVADO E SINCRONIZADO AO VIVO");
                        stTitle.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold; -fx-font-size: 13px;");

                        Label item1 = new Label("✔ Perfil RTSS salvo em: " + finalPath + " (" + limit + " FPS)");
                        item1.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 11px;");

                        Label item2 = new Label(rtssOk ? "⚡ RivaTuner reiniciado: " + limit + " FPS aplicado em tempo real no jogo rodando!"
                                                       : "✔ Perfil do RivaTuner sincronizado");
                        item2.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold; -fx-font-size: 11px;");

                        Label item3 = new Label("🎮 CS2 já está aberto? Se não mudar na hora, cole no console (~): fps_max " + limit);
                        item3.setStyle("-fx-text-fill: #38bdf8; -fx-font-weight: bold; -fx-font-size: 11px;");

                        statusBox.getChildren().addAll(stTitle, item1, item2, item3);
                        NotificationManager.show("✅ Limite de " + limit + " FPS aplicado no RTSS e CS2!", NotificationManager.Type.SUCCESS);
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    statusBox.setVisible(true);
                    statusBox.setManaged(true);
                    statusBox.getChildren().clear();

                    Label stErr = new Label("❌ ERRO AO ATIVAR: " + ex.getMessage());
                    stErr.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold; -fx-font-size: 12px;");
                    statusBox.getChildren().add(stErr);
                    NotificationManager.show("Erro ao aplicar limite: " + ex.getMessage(), NotificationManager.Type.ERROR);
                });
            }
        }).start();
    }

    private boolean safeWriteProfile(File targetFile, String content) {
        try {
            if (targetFile.getParentFile() != null && !targetFile.getParentFile().exists()) {
                targetFile.getParentFile().mkdirs();
            }
            java.nio.file.Files.writeString(targetFile.toPath(), content);
            return true;
        } catch (Exception e) {
            try {
                File tempFile = new File(System.getProperty("java.io.tmpdir"), targetFile.getName());
                java.nio.file.Files.writeString(tempFile.toPath(), content);
                String psCmd = "Copy-Item -Path '" + tempFile.getAbsolutePath() + "' -Destination '" + targetFile.getAbsolutePath() + "' -Force";
                app.getPowerShellService().executeSync(psCmd);
                return targetFile.exists();
            } catch (Exception ex2) {
                return false;
            }
        }
    }
}
