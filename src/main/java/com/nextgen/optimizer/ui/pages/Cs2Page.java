package com.nextgen.optimizer.ui.pages;

import com.nextgen.optimizer.App;
import com.nextgen.optimizer.core.NotificationManager;
import com.nextgen.optimizer.model.AppSettings;
import com.nextgen.optimizer.services.Cs2ConfigService;
import com.nextgen.optimizer.tweaks.Tweak;
import com.nextgen.optimizer.tweaks.TweakService;
import com.nextgen.optimizer.ui.components.*;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Counter-Strike 2 hub: automatic Game Booster, Windows tweaks specific to
 * cs2.exe, a managed autoexec and an optional RTSS frame limiter.
 */
public class Cs2Page extends VBox {

    private static final String[] RELATED_TWEAKS = {
            "cs2-priority", "cs2-gpu-pref", "cs2-fso-off", "windowed-opt", "game-dvr-off",
            "mmcss-games", "mouse-accel-off", "sticky-keys-off", "usb-suspend-off"};

    private final App app;
    private final Label installChip = new Label("Procurando…");
    private final Label runningChip = new Label();
    private final List<TweakRow> tweakRows = new ArrayList<>();
    private final TextArea preview = new TextArea();

    public Cs2Page(App app) {
        this.app = app;
        getStyleClass().add("page-container");
        setSpacing(18);

        installChip.getStyleClass().add("chip");
        runningChip.getStyleClass().add("chip");

        getChildren().addAll(
                buildHeader(),
                new ResponsiveGrid(380, 2, buildBooster(), buildAutoexec()),
                buildTweaks(),
                new ResponsiveGrid(380, 2, buildRtss(), buildInGameTips()));
        detect();
    }

    private Node buildHeader() {
        HBox header = Ui.pageHeader("mdi2t-target", "Counter-Strike 2",
                "Mais FPS, 1% Low estável e menos input lag: Windows, autoexec e Game Booster afinados para o CS2.",
                installChip, runningChip);
        try {
            ImageView logo = new ImageView(new Image(getClass().getResourceAsStream("/cs2_logo.png")));
            logo.setFitWidth(34);
            logo.setPreserveRatio(true);
            logo.setSmooth(true);
            StackPane tile = (StackPane) header.getChildren().get(0);
            tile.getChildren().setAll(logo);
        } catch (Exception ignored) {}
        return header;
    }

    private void detect() {
        Ui.async(() -> app.getSteamLocator().cs2Root(), root -> {
            installChip.getStyleClass().removeAll("chip-ok", "chip-warn");
            if (root != null) {
                installChip.setText("INSTALADO");
                installChip.getStyleClass().add("chip-ok");
                installChip.setTooltip(new javafx.scene.control.Tooltip(root.toString()));
            } else {
                installChip.setText("NÃO ENCONTRADO");
                installChip.getStyleClass().add("chip-warn");
            }
        });
        Ui.async(() -> app.getGameBoosterService().findRunningGame().isPresent(), running -> {
            boolean on = Boolean.TRUE.equals(running);
            runningChip.setText(on ? "● EM EXECUÇÃO" : "FECHADO");
            runningChip.getStyleClass().removeAll("chip-live", "chip-muted");
            runningChip.getStyleClass().add(on ? "chip-live" : "chip-muted");
        });
    }

    // ── Game Booster ────────────────────────────────────────────────

    private Node buildBooster() {
        AppSettings s = app.getSettings();
        ToggleSwitch enabled = new ToggleSwitch(s.isBoosterEnabled());
        ToggleSwitch priority = new ToggleSwitch(s.isBoosterHighPriority());
        ToggleSwitch plan = new ToggleSwitch(s.isBoosterPowerPlan());
        ToggleSwitch ram = new ToggleSwitch(s.isBoosterCleanRam());
        TextField games = new TextField(s.getBoosterGames());
        games.getStyleClass().add("search-field");
        games.setPromptText("cs2.exe, outro-jogo.exe");

        Runnable save = () -> {
            s.setBoosterEnabled(enabled.isSelected());
            s.setBoosterHighPriority(priority.isSelected());
            s.setBoosterPowerPlan(plan.isSelected());
            s.setBoosterCleanRam(ram.isSelected());
            s.setBoosterGames(games.getText());
            s.save();
            app.applyBoosterSettings();
        };
        enabled.setOnAction(e -> {
            save.run();
            NotificationManager.show(enabled.isSelected() ? "Game Booster ligado: aguardando o jogo abrir." : "Game Booster desligado.",
                    NotificationManager.Type.INFO);
        });
        priority.setOnAction(e -> save.run());
        plan.setOnAction(e -> save.run());
        ram.setOnAction(e -> save.run());
        games.focusedProperty().addListener((o, a, focused) -> {
            if (!focused) save.run();
        });
        enabled.setDisable(!app.isElevatedProcess());

        VBox card = Ui.card("accent-card");
        card.getChildren().addAll(
                Ui.cardHeader("mdi2r-rocket-launch-outline", "Game Booster automático",
                        "Otimiza só enquanto o jogo está aberto e desfaz tudo ao fechar.", enabled),
                optionRow(priority, "Prioridade alta de CPU para o jogo"),
                optionRow(plan, "Plano Desempenho Máximo durante a partida"),
                optionRow(ram, "Liberar cache de RAM ao abrir o jogo"),
                new Label("Jogos monitorados"), games);
        return card;
    }

    private HBox optionRow(ToggleSwitch toggle, String text) {
        Label l = new Label(text);
        l.getStyleClass().add("option-title");
        l.setWrapText(true);
        HBox.setHgrow(l, Priority.ALWAYS);
        l.setMaxWidth(Double.MAX_VALUE);
        HBox row = new HBox(12, l, toggle);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("option-row");
        return row;
    }

    // ── autoexec ────────────────────────────────────────────────────

    private Node buildAutoexec() {
        ComboBox<String> fps = new ComboBox<>();
        fps.getItems().addAll("0 — sem limite (máximo FPS)", "400", "300", "237 — monitor 240 Hz", "141 — monitor 144 Hz");
        fps.getSelectionModel().select(0);
        fps.setMaxWidth(Double.MAX_VALUE);
        ToggleSwitch sleep = new ToggleSwitch(true);
        ToggleSwitch rate = new ToggleSwitch(true);
        ToggleSwitch hud = new ToggleSwitch(false);

        preview.setEditable(false);
        preview.setPrefRowCount(7);
        preview.getStyleClass().add("code-area");

        Runnable updatePreview = () -> preview.setText(String.join("\n",
                app.getCs2ConfigService().buildLines(settings(fps, sleep, rate, hud))));
        fps.valueProperty().addListener((o, a, b) -> updatePreview.run());
        sleep.setOnAction(e -> updatePreview.run());
        rate.setOnAction(e -> updatePreview.run());
        hud.setOnAction(e -> updatePreview.run());
        updatePreview.run();

        ActionButton write = new ActionButton("Salvar no autoexec.cfg", "primary");
        write.setOnAction(e -> Ui.run(write, () -> app.getCs2ConfigService().write(settings(fps, sleep, rate, hud)), r -> {
            if (r != null) NotificationManager.show(r.message(), r.success() ? NotificationManager.Type.SUCCESS : NotificationManager.Type.WARNING);
        }));
        ActionButton remove = new ActionButton("Remover bloco", "default");
        remove.setOnAction(e -> Ui.run(remove, () -> app.getCs2ConfigService().removeBlock(), r -> {
            if (r != null) NotificationManager.show(r.message(), r.success() ? NotificationManager.Type.SUCCESS : NotificationManager.Type.WARNING);
        }));
        ActionButton launch = new ActionButton("Copiar opção de inicialização", "default");
        launch.setOnAction(e -> {
            copy(Cs2ConfigService.LAUNCH_OPTIONS);
            NotificationManager.success("Copiado: " + Cs2ConfigService.LAUNCH_OPTIONS + " — cole em Steam › CS2 › Propriedades › Opções de inicialização.");
        });

        VBox card = Ui.card();
        card.getChildren().addAll(
                Ui.cardHeader("mdi2c-console", "autoexec.cfg otimizado",
                        "Grava só um bloco marcado; suas binds e configurações são preservadas (com backup)."),
                new Label("Limite de FPS (fps_max)"), fps,
                optionRow(sleep, "Low latency sleep após o tick do cliente"),
                optionRow(rate, "Taxa de rede máxima (rate 786432)"),
                optionRow(hud, "Telemetria de FPS/ping no HUD"),
                preview,
                new FlowPane(8, 8, write, remove, launch));
        return card;
    }

    private Cs2ConfigService.Settings settings(ComboBox<String> fps, ToggleSwitch sleep, ToggleSwitch rate, ToggleSwitch hud) {
        String v = fps.getValue() == null ? "0" : fps.getValue().split(" ")[0];
        return new Cs2ConfigService.Settings(Integer.parseInt(v), 120, sleep.isSelected(), rate.isSelected(), hud.isSelected());
    }

    // ── Windows tweaks for CS2 ─────────────────────────────────────

    private Node buildTweaks() {
        ActionButton applyAll = new ActionButton("Aplicar todos", "primary");
        applyAll.setDisable(!app.isElevatedProcess());
        applyAll.setOnAction(e -> {
            List<Tweak> tweaks = tweakRows.stream().map(TweakRow::tweak).toList();
            Ui.run(applyAll, () -> app.getTweakService().applyAll(tweaks, null), results -> {
                if (results == null) return;
                long changed = results.stream().filter(TweakService.Result::changed).count();
                NotificationManager.success(changed + " ajustes aplicados para o CS2.");
                tweakRows.forEach(TweakRow::refresh);
                app.getNavigationManager().invalidate("tweaks", "dashboard");
            });
        });
        VBox list = new VBox(0);
        list.getStyleClass().add("tweak-list");
        for (String id : RELATED_TWEAKS) {
            Tweak t = app.getTweakService().find(id);
            if (t == null) continue;
            TweakRow row = new TweakRow(t, app.getTweakService(), () -> app.getNavigationManager().invalidate("tweaks", "dashboard"));
            row.setDisable(!app.isElevatedProcess());
            tweakRows.add(row);
            list.getChildren().add(row);
        }
        VBox card = Ui.card();
        card.getChildren().addAll(Ui.cardHeader("mdi2m-microsoft-windows", "Windows afinado para o CS2",
                "Todos reversíveis individualmente.", applyAll), list);
        return card;
    }

    // ── RTSS ────────────────────────────────────────────────────────

    private Node buildRtss() {
        Path rtssDir = findRtss();
        Label status = Ui.muted(rtssDir != null
                ? "RTSS encontrado. O perfil é gravado só para cs2.exe — seu perfil Global não é alterado."
                : "RivaTuner Statistics Server não encontrado. Instale-o (vem com o MSI Afterburner) para usar o limitador externo.");
        FlowPane buttons = new FlowPane(8, 8);
        for (int limit : new int[]{0, 141, 237, 357}) {
            ActionButton b = new ActionButton(limit == 0 ? "Sem limite" : limit + " FPS", limit == 0 ? "default" : "default");
            b.setDisable(rtssDir == null);
            b.setOnAction(e -> Ui.run(b, () -> writeRtssProfile(rtssDir, limit), msg -> {
                if (msg != null) NotificationManager.show(msg, NotificationManager.Type.INFO);
            }));
            buttons.getChildren().add(b);
        }
        VBox card = Ui.card();
        card.getChildren().addAll(
                Ui.cardHeader("mdi2s-speedometer", "Limitador de FPS (RTSS)",
                        "Limitar o FPS logo abaixo da taxa do monitor estabiliza o frametime e o 1% Low."),
                status, buttons,
                Ui.muted("Com G-SYNC/FreeSync: limite 3 FPS abaixo do Hz do monitor. Sem sincronização adaptativa, \"Sem limite\" dá a menor latência."));
        return card;
    }

    private Path findRtss() {
        for (String base : new String[]{"C:\\Program Files (x86)\\RivaTuner Statistics Server", "C:\\Program Files\\RivaTuner Statistics Server"}) {
            Path p = Path.of(base);
            if (Files.isRegularFile(p.resolve("RTSS.exe"))) return p;
        }
        return null;
    }

    private String writeRtssProfile(Path rtssDir, int limit) {
        String content = "[Framerate]\r\nLimit=" + limit + "\r\nLimitDenominator=1\r\n";
        Path profile = rtssDir.resolve("Profiles").resolve("cs2.exe.cfg");
        try {
            Files.createDirectories(profile.getParent());
            if (Files.exists(profile)) Files.copy(profile, profile.resolveSibling("cs2.exe.cfg.nextgen.bak"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(profile, content, StandardCharsets.US_ASCII);
        } catch (Exception e) {
            return "Não foi possível gravar o perfil do RTSS: " + e.getMessage();
        }
        boolean running = ProcessHandle.allProcesses().anyMatch(p -> p.info().command()
                .map(c -> c.toLowerCase().endsWith("rtss.exe")).orElse(false));
        if (running) {
            // RTSS reloads profiles on restart.
            app.getPowerShellService().executeSync("Stop-Process -Name RTSS -Force -ErrorAction SilentlyContinue; Start-Sleep -Milliseconds 300; "
                    + "Start-Process -FilePath '" + rtssDir.resolve("RTSS.exe").toString().replace("'", "''") + "'");
        }
        return limit == 0 ? "RTSS: limite do CS2 removido." : "RTSS: CS2 limitado a " + limit + " FPS" + (running ? " (RTSS reiniciado)." : ".");
    }

    // ── In-game tips ────────────────────────────────────────────────

    private Node buildInGameTips() {
        VBox card = Ui.card();
        card.getChildren().add(Ui.cardHeader("mdi2l-lightbulb-on-outline", "Configurações dentro do jogo", "Recomendações competitivas."));
        String[][] tips = {
                {"NVIDIA Reflex", "Ativado + Boost — a maior redução de input lag disponível."},
                {"Modo de exibição", "Tela cheia. Janela sem borda só com as otimizações para jogos em janela."},
                {"Contraste de jogadores", "Ativado — facilita enxergar inimigos."},
                {"Sombras / Detalhes", "Sombras em Alto ajudam a ver oponentes; o resto em Baixo para FPS."},
                {"FSR / Upscaling", "Desligado. Use resolução nativa ou esticada, sem upscaler."},
                {"Opções de inicialização", "Apenas \"+exec autoexec\". Evite -threads e -high (o NextGen X cuida da prioridade)."}};
        for (String[] tip : tips) {
            Label t = new Label(tip[0]);
            t.getStyleClass().add("option-title");
            card.getChildren().add(new VBox(2, t, Ui.muted(tip[1])));
        }
        return card;
    }

    private static void copy(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }
}
