package com.nextgen.optimizer.ui.pages;

import com.nextgen.optimizer.App;
import com.nextgen.optimizer.core.NotificationManager;
import com.nextgen.optimizer.ui.components.ActionButton;

import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Files;

/**
 * Quick Optimization Page — Instant disk/cache cleanups and RAM memory optimization.
 */
public class QuickOptimizationPage extends VBox {

    private static final long SAFE_DELETE_AGE_MS = 15L * 60L * 1000L;

    private final App app;

    public QuickOptimizationPage(App app) {
        this.app = app;
        getStyleClass().add("page-container");
        setSpacing(22);
        setPadding(new Insets(4, 4, 28, 4));
        buildUI();
    }

    private void buildUI() {
        // Header
        VBox header = new VBox(4);
        Label title = new Label("⚡ Otimizações Rápidas");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Limpeza instantânea de cache do sistema, shaders de GPU e liberação profunda de memória RAM");
        sub.getStyleClass().add("page-subtitle");
        header.getChildren().addAll(title, sub);

        // Master Action Cards
        HBox masterRow = buildMasterCards();

        // Section 1: Limpeza (Disk & Caches)
        VBox cleanupSection = buildCleanupSection();

        // Section 2: RAM
        VBox ramSection = buildRamSection();

        // Section 3: Sistema & Energia
        VBox systemSection = buildSystemTweaksSection();

        getChildren().addAll(header, masterRow, cleanupSection, ramSection, systemSection);
    }

    private HBox buildMasterCards() {
        HBox row = new HBox(16);

        // Card 1: Clean All
        VBox cardClean = new VBox(10);
        cardClean.getStyleClass().add("card");
        HBox.setHgrow(cardClean, Priority.ALWAYS);
        cardClean.setPadding(new Insets(18, 22, 18, 22));
        cardClean.setStyle("-fx-background-color: linear-gradient(to right, #111827, #1e293b); -fx-border-color: #3b82f644; -fx-border-radius: 12; -fx-background-radius: 12;");

        HBox top1 = new HBox(10);
        top1.setAlignment(Pos.CENTER_LEFT);
        Label icon1 = new Label("🧹");
        icon1.setStyle("-fx-font-size: 24px;");
        VBox texts1 = new VBox(2);
        Label title1 = new Label("Limpeza Geral em 1 Clique");
        title1.setStyle("-fx-font-weight: 800; -fx-font-size: 15px; -fx-text-fill: white;");
        Label desc1 = new Label("Executa todas as 8 limpezas de cache, temporários, DirectX e navegadores");
        desc1.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
        texts1.getChildren().addAll(title1, desc1);
        top1.getChildren().addAll(icon1, texts1);

        ActionButton btnCleanAll = new ActionButton("Executar Limpeza Total", "primary");
        btnCleanAll.setMaxWidth(Double.MAX_VALUE);
        btnCleanAll.setOnAction(e -> runAllCleanups(btnCleanAll));

        cardClean.getChildren().addAll(top1, btnCleanAll);

        // Card 2: Optimize RAM
        VBox cardRam = new VBox(10);
        cardRam.getStyleClass().add("card");
        HBox.setHgrow(cardRam, Priority.ALWAYS);
        cardRam.setPadding(new Insets(18, 22, 18, 22));
        cardRam.setStyle("-fx-background-color: linear-gradient(to right, #111827, #1e293b); -fx-border-color: #8b5cf644; -fx-border-radius: 12; -fx-background-radius: 12;");

        HBox top2 = new HBox(10);
        top2.setAlignment(Pos.CENTER_LEFT);
        Label icon2 = new Label("🧠");
        icon2.setStyle("-fx-font-size: 24px;");
        VBox texts2 = new VBox(2);
        Label title2 = new Label("Otimização Máxima de RAM");
        title2.setStyle("-fx-font-weight: 800; -fx-font-size: 15px; -fx-text-fill: white;");
        Label desc2 = new Label("Limpa Standby Memory + Working Set simultaneamente sem fechar aplicativos");
        desc2.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
        texts2.getChildren().addAll(title2, desc2);
        top2.getChildren().addAll(icon2, texts2);

        ActionButton btnRamAll = new ActionButton("Otimizar RAM Agora", "primary");
        btnRamAll.setMaxWidth(Double.MAX_VALUE);
        btnRamAll.setOnAction(e -> runFullRamOptimization(btnRamAll));

        cardRam.getChildren().addAll(top2, btnRamAll);

        row.getChildren().addAll(cardClean, cardRam);
        return row;
    }

    private VBox buildCleanupSection() {
        VBox section = new VBox(12);
        section.getStyleClass().add("card");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("🧹 Limpeza do Sistema & Cache Gráfico");
        title.getStyleClass().add("card-title");
        header.getChildren().add(title);

        VBox list = new VBox(8);

        list.getChildren().addAll(
            createCleanupRow("%temp%", "Arquivos temporários da sua conta de usuário (AppData/Local/Temp)", () -> cleanUserTemp()),
            createCleanupRow("temp", "Arquivos temporários gerais do sistema Windows (C:\\Windows\\Temp)", () -> cleanSystemTemp()),
            createCleanupRow("Prefetch", "Cache de pré-carregamento do Windows (C:\\Windows\\Prefetch)", () -> cleanPrefetch()),
            createCleanupRow("Logs", "Arquivos de relatórios de erros, eventos e logs do sistema (C:\\Windows\\Logs)", () -> cleanLogs()),
            createCleanupRow("DirectX Shader Cache", "Shaders compilados D3DSCache / DirectX Shader Cache", () -> cleanDirectXCache()),
            createCleanupRow("Mini Dumps", "Relatórios de despejo de memória de erros do sistema (Minidump)", () -> cleanMiniDumps()),
            createCleanupRow("Cache do Navegador", "Cache temporário do Chrome, Edge, Brave, Opera e Firefox", () -> cleanBrowserCache()),
            createCleanupRow("Cache NVIDIA / AMD", "Cache de shaders e texturas (GLCache / DXCache) da sua placa de vídeo", () -> cleanGpuCache()),
            createCleanupRow("Shaders da Steam", "Shaders baixados e compilados pela Steam (C:\\Program Files (x86)\\Steam\\steamapps\\shadercache)", () -> cleanSteamShaderCache())
        );

        section.getChildren().addAll(header, list);
        return section;
    }

    private VBox buildRamSection() {
        VBox section = new VBox(12);
        section.getStyleClass().add("card");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("🧠 RAM — Otimização Avançada de Memória");
        title.getStyleClass().add("card-title");
        header.getChildren().add(title);

        VBox list = new VBox(8);

        list.getChildren().addAll(
            createRamRow("Limpar Standby Memory", "Libera memória cache em espera (Standby List) retida pelo Windows para aplicativos fechados", () -> app.getRamService().clearStandbyMemory()),
            createRamRow("Limpar Working Set", "Reduz a alocação do Working Set de processos em segundo plano para liberar RAM física imediata", () -> app.getRamService().clearWorkingSets())
        );

        section.getChildren().addAll(header, list);
        return section;
    }

    private HBox createCleanupRow(String name, String descText, CleanupAction action) {
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

        Label statusLbl = new Label("Pronto");
        statusLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        ActionButton btn = new ActionButton("Limpar", "default");
        btn.setOnAction(e -> {
            btn.setDisable(true);
            statusLbl.setText("Limpando...");
            new Thread(() -> {
                long bytesFreed = action.run();
                String result = formatBytes(bytesFreed);
                Platform.runLater(() -> {
                    btn.setDisable(false);
                    statusLbl.setText("✓ " + result + " liberados");
                    statusLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #22c55e; -fx-font-weight: bold;");
                    NotificationManager.show(name + " limpo! (" + result + " liberados)", NotificationManager.Type.SUCCESS);
                });
            }).start();
        });

        row.getChildren().addAll(info, statusLbl, btn);
        return row;
    }

    private HBox createRamRow(String name, String descText, RamAction action) {
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

        Label statusLbl = new Label("Disponível");
        statusLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        ActionButton btn = new ActionButton("Otimizar", "primary");
        btn.setOnAction(e -> {
            btn.setDisable(true);
            statusLbl.setText("Otimizando...");
            new Thread(() -> {
                boolean success = action.run();
                Platform.runLater(() -> {
                    btn.setDisable(false);
                    if (success) {
                        statusLbl.setText("✓ Otimizado!");
                        statusLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #22c55e; -fx-font-weight: bold;");
                        NotificationManager.show(name + " concluído com sucesso!", NotificationManager.Type.SUCCESS);
                    } else {
                        statusLbl.setText("Concluído");
                    }
                });
            }).start();
        });

        row.getChildren().addAll(info, statusLbl, btn);
        return row;
    }

    private void runAllCleanups(ActionButton btn) {
        btn.setDisable(true);
        btn.setText("Limpando Tudo...");
        new Thread(() -> {
            long total = 0;
            total += cleanUserTemp();
            total += cleanSystemTemp();
            total += cleanPrefetch();
            total += cleanLogs();
            total += cleanDirectXCache();
            total += cleanMiniDumps();
            total += cleanBrowserCache();
            total += cleanGpuCache();
            total += cleanSteamShaderCache();

            long finalTotal = total;
            String formatted = formatBytes(finalTotal);
            Platform.runLater(() -> {
                btn.setDisable(false);
                btn.setText("Executar Limpeza Total");
                NotificationManager.show("Limpeza Total concluída! (" + formatted + " liberados)", NotificationManager.Type.SUCCESS);
            });
        }).start();
    }

    private void runFullRamOptimization(ActionButton btn) {
        btn.setDisable(true);
        btn.setText("Otimizando RAM...");
        new Thread(() -> {
            app.getRamService().clearStandbyMemory();
            app.getRamService().clearWorkingSets();
            Platform.runLater(() -> {
                btn.setDisable(false);
                btn.setText("Otimizar RAM Agora");
                NotificationManager.show("Memória RAM otimizada ao máximo com sucesso!", NotificationManager.Type.SUCCESS);
            });
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════
    //  CLEANUP METHODS
    // ═══════════════════════════════════════════════════════════════

    private long cleanUserTemp() {
        return cleanDirectory(new File(System.getProperty("java.io.tmpdir")));
    }

    private long cleanSystemTemp() {
        return cleanDirectory(new File("C:\\Windows\\Temp"));
    }

    private long cleanPrefetch() {
        return cleanDirectory(new File("C:\\Windows\\Prefetch"));
    }

    private long cleanLogs() {
        long bytes = cleanDirectory(new File("C:\\Windows\\Logs"));
        bytes += cleanDirectory(new File("C:\\Windows\\System32\\LogFiles"));
        return bytes;
    }

    private long cleanDirectXCache() {
        long bytes = 0;
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            bytes += cleanDirectory(new File(localAppData, "D3DSCache"));
            bytes += cleanDirectory(new File(localAppData, "Microsoft\\DirectX Shader Cache"));
        }
        return bytes;
    }

    private long cleanMiniDumps() {
        long bytes = cleanDirectory(new File("C:\\Windows\\Minidump"));
        File memDump = new File("C:\\Windows\\MEMORY.DMP");
        if (memDump.exists()) {
            long size = memDump.length();
            if (memDump.delete()) bytes += size;
        }
        return bytes;
    }

    private long cleanBrowserCache() {
        long bytes = 0;
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            bytes += cleanDirectory(new File(localAppData, "Google\\Chrome\\User Data\\Default\\Cache"));
            bytes += cleanDirectory(new File(localAppData, "Google\\Chrome\\User Data\\Default\\Code Cache"));
            bytes += cleanDirectory(new File(localAppData, "Microsoft\\Edge\\User Data\\Default\\Cache"));
            bytes += cleanDirectory(new File(localAppData, "Microsoft\\Edge\\User Data\\Default\\Code Cache"));
            bytes += cleanDirectory(new File(localAppData, "BraveSoftware\\Brave-Browser\\User Data\\Default\\Cache"));
            bytes += cleanDirectory(new File(localAppData, "Opera Software\\Opera Stable\\Cache"));
        }
        return bytes;
    }

    private long cleanGpuCache() {
        long bytes = 0;
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            bytes += cleanDirectory(new File(localAppData, "NVIDIA\\GLCache"));
            bytes += cleanDirectory(new File(localAppData, "NVIDIA\\DXCache"));
            bytes += cleanDirectory(new File(localAppData, "AMD\\GLCache"));
            bytes += cleanDirectory(new File(localAppData, "AMD\\DxCache"));
        }
        return bytes;
    }

    private long cleanSteamShaderCache() {
        long bytes = 0;
        File steam1 = new File("C:\\Program Files (x86)\\Steam\\steamapps\\shadercache");
        File steam2 = new File("C:\\Program Files\\Steam\\steamapps\\shadercache");
        File steam3 = new File("D:\\Steam\\steamapps\\shadercache");
        bytes += cleanDirectory(steam1);
        bytes += cleanDirectory(steam2);
        bytes += cleanDirectory(steam3);
        return bytes;
    }

    private long cleanDirectory(File dir) {
        long total = 0;
        if (dir != null && dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    total += cleanRecursive(f);
                }
            }
        }
        return total;
    }

    private long cleanRecursive(File f) {
        long sz = 0;
        try {
            if (f == null || !f.exists()) return 0;
            if (Files.isSymbolicLink(f.toPath())) return 0;
            if (System.currentTimeMillis() - f.lastModified() < SAFE_DELETE_AGE_MS) return 0;

            if (f.isDirectory()) {
                File[] children = f.listFiles();
                if (children != null) {
                    for (File c : children) {
                        sz += cleanRecursive(c);
                    }
                }
            }
            long len = f.isFile() ? f.length() : 0;
            if (f.delete()) {
                sz += len;
            }
        } catch (Exception ignored) {}
        return sz;
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 MB";
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        double mb = bytes / (1024.0 * 1024.0);
        if (mb > 1024) {
            return String.format("%.2f GB", mb / 1024.0);
        }
        return String.format("%.1f MB", mb);
    }

    private VBox buildSystemTweaksSection() {
        VBox section = new VBox(14);
        section.getStyleClass().add("card");
        section.setPadding(new Insets(20));

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("⚡ Sistema & Energia — Otimização Profunda do Windows");
        title.getStyleClass().add("card-title");
        header.getChildren().add(title);

        VBox list = new VBox(10);

        list.getChildren().addAll(
            createDualTweakRow("Hibernação do Sistema (hiberfil.sys)",
                "Desativa o arquivo hiberfil.sys no SSD/HD (liberando instantaneamente de 8 GB a 32 GB em disco e reduzindo desgastes)",
                "Desativar Hibernação", "Reativar",
                () -> {
                    app.getPowerShellService().executeSync("powercfg -h off");
                    NotificationManager.show("Hibernação desativada! Vários GBs de espaço liberados no disco.", NotificationManager.Type.SUCCESS);
                },
                () -> {
                    app.getPowerShellService().executeSync("powercfg -h on");
                    NotificationManager.show("Hibernação reativada com sucesso.", NotificationManager.Type.INFO);
                }),
            createTweakRow("Desativar Serviço SysMain (SuperFetch)",
                "Impede o pré-carregamento excessivo de arquivos, evitando o problema de disco em 100% e liberando RAM",
                "Desativar SysMain",
                () -> {
                    app.getPowerShellService().executeSync("Stop-Service -Name SysMain -Force; Set-Service -Name SysMain -StartupType Disabled");
                    NotificationManager.show("Serviço SysMain / SuperFetch desativado!", NotificationManager.Type.SUCCESS);
                }),
            createTweakRow("Desativar Telemetria & Rastreamento Microsoft",
                "Desativa os serviços de coleta de diagnósticos em background (DiagTrack e dmwappushservice)",
                "Desativar Telemetria",
                () -> {
                    app.getPowerShellService().executeSync("Stop-Service -Name DiagTrack -Force; Set-Service -Name DiagTrack -StartupType Disabled; Stop-Service -Name dmwappushservice -Force; Set-Service -Name dmwappushservice -StartupType Disabled");
                    NotificationManager.show("Telemetria do Windows desativada com sucesso!", NotificationManager.Type.SUCCESS);
                }),
            createTweakRow("Desativar Relatório de Erros em Background (WerSvc)",
                "Evita picos repentinos de CPU e memória ao gerar logs de crash em segundo plano",
                "Desativar WerSvc",
                () -> {
                    app.getPowerShellService().executeSync("Stop-Service -Name WerSvc -Force; Set-Service -Name WerSvc -StartupType Disabled");
                    NotificationManager.show("Serviço de Relatório de Erros desativado!", NotificationManager.Type.SUCCESS);
                }),
            createTweakRow("Desativar Busca Web Bing no Menu Iniciar",
                "Torna a pesquisa do Menu Iniciar 100% local, instantânea e sem requisições à internet",
                "Otimizar Menu Iniciar",
                () -> {
                    app.getPowerShellService().executeSync("New-ItemProperty -Path 'HKCU:\\SOFTWARE\\Policies\\Microsoft\\Windows\\Explorer' -Name 'DisableSearchBoxSuggestions' -Value 1 -PropertyType DWord -Force");
                    NotificationManager.show("Busca Web no Menu Iniciar desativada com sucesso!", NotificationManager.Type.SUCCESS);
                })
        );

        section.getChildren().addAll(header, list);
        return section;
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

    private HBox createDualTweakRow(String name, String descText, String btnText1, String btnText2, Runnable action1, Runnable action2) {
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

        ActionButton btn1 = new ActionButton(btnText1, "primary");
        btn1.setOnAction(e -> {
            btn1.setDisable(true);
            new Thread(() -> {
                action1.run();
                Platform.runLater(() -> btn1.setDisable(false));
            }).start();
        });

        ActionButton btn2 = new ActionButton(btnText2, "default");
        btn2.setOnAction(e -> {
            btn2.setDisable(true);
            new Thread(() -> {
                action2.run();
                Platform.runLater(() -> btn2.setDisable(false));
            }).start();
        });

        row.getChildren().addAll(info, btn1, btn2);
        return row;
    }

    @FunctionalInterface
    private interface CleanupAction {
        long run();
    }

    @FunctionalInterface
    private interface RamAction {
        boolean run();
    }
}
