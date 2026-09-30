package com.nextgen.optimizer;

import com.nextgen.optimizer.core.Brand;
import com.nextgen.optimizer.core.MemoryTrimmer;
import com.nextgen.optimizer.core.NavigationManager;
import com.nextgen.optimizer.core.NotificationManager;
import com.nextgen.optimizer.model.AppSettings;
import com.nextgen.optimizer.nativeapi.WinNative;
import com.nextgen.optimizer.overlay.OverlayWindow;
import com.nextgen.optimizer.services.*;
import com.nextgen.optimizer.tweaks.TweakCatalog;
import com.nextgen.optimizer.tweaks.TweakContext;
import com.nextgen.optimizer.tweaks.TweakService;
import com.nextgen.optimizer.tweaks.TweakStore;
import com.nextgen.optimizer.ui.components.BrandMark;
import com.nextgen.optimizer.ui.components.Ui;
import com.sun.jna.platform.win32.Advapi32Util;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * NextGen X — application shell: services, window chrome, responsive sidebar,
 * status bar and tray integration.
 */
public class App extends Application {

    private static final double COLLAPSE_BELOW = 1180;

    private Stage primaryStage;
    private AppSettings settings;
    private boolean elevated;

    // Services
    private PowerShellService powerShellService;
    private RegistryService registryService;
    private BackupService backupService;
    private SystemInfoService systemInfoService;
    private ProcessService processService;
    private NetworkService networkService;
    private GpuService gpuService;
    private CpuService cpuService;
    private StorageService storageService;
    private MemoryService memoryService;
    private SteamLocator steamLocator;
    private TweakService tweakService;
    private SafetyService safetyService;
    private CleanupService cleanupService;
    private GameBoosterService gameBoosterService;
    private Cs2ConfigService cs2ConfigService;

    // UI
    private NavigationManager navigationManager;
    private StackPane contentArea;
    private VBox sidebar;
    private Label pageCrumb;
    private Label boosterChip;
    private Label guardStatus;
    private OverlayWindow overlayWindow;
    private boolean sidebarForcedCollapsed;

    // Window state
    private double dragX, dragY;
    private boolean maximized;
    private Rectangle2D restoreBounds;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.initStyle(StageStyle.TRANSPARENT);

        settings = AppSettings.load(AppSettings.defaultPath());
        elevated = isElevated();
        initializeServices();

        StackPane root = new StackPane(createRootLayout());
        root.getStyleClass().add("window-shadow-host");
        NotificationManager.init(root);

        Scene scene = new Scene(root, 1320, 840);
        scene.setFill(Color.TRANSPARENT);
        for (String css : new String[]{"/css/main.css", "/css/components.css", "/css/theme.css"}) {
            scene.getStylesheets().add(getClass().getResource(css).toExternalForm());
        }
        scene.widthProperty().addListener((o, a, w) -> applySidebarMode(w.doubleValue()));

        stage.setScene(scene);
        stage.setTitle(Brand.FULL_NAME);
        try {
            stage.getIcons().add(new Image(getClass().getResourceAsStream("/logo.png")));
        } catch (Exception ignored) {}
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        fitToScreen(stage);
        enableResize(scene, root);

        root.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(450), root);
        fadeIn.setToValue(1);
        fadeIn.setInterpolator(Interpolator.EASE_OUT);

        setupSystemTray();
        stage.show();
        fadeIn.play();
        applySidebarMode(scene.getWidth());

        navigationManager.navigateTo("dashboard");
        startBackgroundFeatures();
        if (!elevated) {
            NotificationManager.warning("Sem privilégios de administrador: ajustes do sistema e limpeza de RAM ficam indisponíveis.");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Services
    // ═══════════════════════════════════════════════════════════════

    private void initializeServices() {
        powerShellService = new PowerShellService();
        registryService = new RegistryService();
        backupService = new BackupService(registryService);
        systemInfoService = new SystemInfoService();
        processService = new ProcessService(powerShellService);
        networkService = new NetworkService(powerShellService, registryService, backupService);
        gpuService = new GpuService(powerShellService, systemInfoService);
        cpuService = new CpuService(powerShellService, registryService, backupService, systemInfoService);
        storageService = new StorageService(powerShellService, systemInfoService);
        memoryService = new MemoryService();
        steamLocator = new SteamLocator(registryService);
        safetyService = new SafetyService(powerShellService);
        cleanupService = new CleanupService(powerShellService, steamLocator);
        cs2ConfigService = new Cs2ConfigService(steamLocator);

        TweakContext ctx = new TweakContext(registryService, TweakContext.nativePower(powerShellService), powerShellService);
        tweakService = new TweakService(ctx, TweakStore.defaultStore(), TweakCatalog.build(registryService, steamLocator));
        gameBoosterService = new GameBoosterService(ctx, memoryService);

        systemInfoService.startMonitoring();
    }

    private void startBackgroundFeatures() {
        gameBoosterService.setListener(new GameBoosterService.Listener() {
            public void onGameStarted(String exe, String summary) {
                Platform.runLater(() -> {
                    NotificationManager.success("Game Booster: " + exe + " detectado — " + summary + ".");
                    updateBoosterChip();
                });
            }

            public void onGameStopped(String exe) {
                Platform.runLater(() -> {
                    NotificationManager.info("Game Booster: " + exe + " fechado. Configurações de sessão restauradas.");
                    updateBoosterChip();
                });
            }
        });
        applyBoosterSettings();
        applyRamGuardSettings();
    }

    /** Re-reads booster settings and starts or stops the watcher accordingly. */
    public void applyBoosterSettings() {
        Set<String> games = Arrays.stream(settings.getBoosterGames().split("[,;\\s]+"))
                .filter(s -> !s.isBlank()).collect(Collectors.toCollection(LinkedHashSet::new));
        gameBoosterService.configure(games, new GameBoosterService.Options(
                settings.isBoosterHighPriority(), settings.isBoosterPowerPlan(), settings.isBoosterCleanRam()));
        if (settings.isBoosterEnabled() && elevated) gameBoosterService.start();
        else gameBoosterService.stop();
        updateBoosterChip();
    }

    public void applyRamGuardSettings() {
        if (settings.isRamGuardEnabled() && elevated) {
            memoryService.startGuard(settings.getRamGuardThreshold(), settings.isRamGuardOnlyWhileGaming(),
                    gameBoosterService::isGameActive,
                    r -> Platform.runLater(this::updateGuardStatus));
        } else {
            memoryService.stopGuard();
        }
        updateGuardStatus();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Layout
    // ═══════════════════════════════════════════════════════════════

    private BorderPane createRootLayout() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");

        contentArea = new StackPane();
        contentArea.setAlignment(Pos.TOP_LEFT);
        contentArea.getStyleClass().add("content-area");

        ScrollPane scrollPane = new ScrollPane(contentArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("content-scroll");

        navigationManager = new NavigationManager(contentArea, scrollPane, this);
        navigationManager.setOnNavigate(def -> pageCrumb.setText(def.label()));

        root.setTop(createTitleBar());
        sidebar = createSidebar();
        root.setLeft(sidebar);
        root.setCenter(scrollPane);
        root.setBottom(createStatusBar());
        return root;
    }

    private HBox createTitleBar() {
        BrandMark mark = new BrandMark(20);
        Label name = new Label(Brand.WORDMARK_PRIMARY);
        name.getStyleClass().add("title-bar-text");
        Label accent = new Label(Brand.WORDMARK_ACCENT);
        accent.getStyleClass().add("title-bar-text-accent");
        HBox word = new HBox(3, name, accent);
        word.setAlignment(Pos.CENTER_LEFT);

        Label sep = new Label("/");
        sep.getStyleClass().add("title-bar-sep");
        pageCrumb = new Label("");
        pageCrumb.getStyleClass().add("title-bar-crumb");

        HBox left = new HBox(10, mark, word, sep, pageCrumb);
        left.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(left, Priority.ALWAYS);

        boosterChip = new Label();
        boosterChip.getStyleClass().add("chip");
        boosterChip.setOnMouseClicked(e -> navigationManager.navigateTo("cs2"));
        Label adminChip = new Label(elevated ? "ADMIN" : "SEM ADMIN");
        adminChip.getStyleClass().addAll("chip", elevated ? "chip-ok" : "chip-warn");

        Label min = windowButton("mdi2w-window-minimize", "window-btn");
        min.setOnMouseClicked(e -> primaryStage.setIconified(true));
        Label max = windowButton("mdi2w-window-maximize", "window-btn");
        max.setOnMouseClicked(e -> toggleMaximize());
        Label close = windowButton("mdi2c-close", "window-btn-close");
        close.setOnMouseClicked(e -> handleClose());

        HBox chips = new HBox(8, boosterChip, adminChip);
        chips.setAlignment(Pos.CENTER_RIGHT);
        chips.setPadding(new Insets(0, 10, 0, 0));
        HBox controls = new HBox(2, min, max, close);
        controls.setAlignment(Pos.CENTER_RIGHT);

        HBox bar = new HBox(left, chips, controls);
        bar.getStyleClass().add("title-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 6, 0, 16));
        bar.setMinHeight(46);
        bar.setPrefHeight(46);

        bar.setOnMousePressed(e -> {
            dragX = e.getSceneX();
            dragY = e.getSceneY();
        });
        bar.setOnMouseDragged(e -> {
            if (maximized) {
                // Drag from maximized: restore under the cursor, like native windows.
                double ratio = dragX / primaryStage.getWidth();
                toggleMaximize();
                dragX = primaryStage.getWidth() * ratio;
            }
            primaryStage.setX(e.getScreenX() - dragX);
            primaryStage.setY(e.getScreenY() - dragY);
        });
        bar.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) toggleMaximize();
        });
        return bar;
    }

    private Label windowButton(String icon, String style) {
        Label b = new Label("", Ui.icon(icon, 15));
        b.getStyleClass().addAll("window-btn-base", style);
        b.setMinSize(42, 30);
        b.setAlignment(Pos.CENTER);
        return b;
    }

    private VBox createSidebar() {
        FontIcon menuIcon = Ui.icon("mdi2m-menu", 20);
        Label toggle = new Label("", menuIcon);
        toggle.getStyleClass().add("sidebar-toggle");
        toggle.setOnMouseClicked(e -> {
            settings.setSidebarCollapsed(!settings.isSidebarCollapsed());
            settings.save();
            applySidebarMode(primaryStage.getScene().getWidth());
        });

        Label tagline = new Label(Brand.TAGLINE.toUpperCase(Locale.ROOT) + "  ·  v" + Brand.VERSION);
        tagline.getStyleClass().add("sidebar-version");
        HBox head = new HBox(10, toggle, tagline);
        head.setAlignment(Pos.CENTER_LEFT);
        head.getStyleClass().add("sidebar-head");

        NavigationManager nav = navigationManager;
        VBox items = new VBox(2,
                section("VISÃO GERAL"),
                nav.createNavItem("dashboard", "mdi2v-view-dashboard-outline", "Painel"),
                nav.createNavItem("full-opt", "mdi2r-rocket-launch-outline", "Otimização Full"),
                nav.createNavItem("monitor", "mdi2c-chart-timeline-variant", "Monitor"),
                section("OTIMIZAR"),
                nav.createNavItem("tweaks", "mdi2t-tune-variant", "Central de Ajustes"),
                nav.createNavItem("cs2", "mdi2t-target", "Counter-Strike 2"),
                nav.createNavItem("ram", "mdi2m-memory", "Memória RAM"),
                nav.createNavItem("cleanup", "mdi2b-broom", "Limpeza"),
                nav.createNavItem("startup", "mdi2p-power", "Inicialização"),
                nav.createNavItem("network", "mdi2w-web", "Rede & Ping"),
                section("HARDWARE"),
                nav.createNavItem("cpu", "mdi2c-chip", "CPU"),
                nav.createNavItem("gpu", "mdi2e-expansion-card-variant", "GPU"),
                nav.createNavItem("storage", "mdi2h-harddisk", "Armazenamento"),
                section("SISTEMA"),
                nav.createNavItem("privacy", "mdi2s-shield-lock-outline", "Privacidade"),
                nav.createNavItem("overlay", "mdi2m-monitor-eye", "Overlay"),
                nav.createNavItem("bios-tips", "mdi2l-lightbulb-on-outline", "Guia BIOS & HW"),
                nav.createNavItem("settings", "mdi2b-backup-restore", "Backups & Ajustes")
        );

        ScrollPane navScroll = new ScrollPane(items);
        navScroll.getStyleClass().addAll("content-scroll", "nav-scroll");
        navScroll.setFitToWidth(true);
        navScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(navScroll, Priority.ALWAYS);

        VBox box = new VBox(8, head, navScroll);
        box.getStyleClass().add("sidebar");
        box.setPadding(new Insets(10, 10, 12, 10));
        return box;
    }

    private Label section(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("nav-section-title");
        VBox.setMargin(l, new Insets(14, 0, 4, 12));
        return l;
    }

    private void applySidebarMode(double width) {
        if (sidebar == null) return;
        sidebarForcedCollapsed = width < COLLAPSE_BELOW;
        boolean collapsed = sidebarForcedCollapsed || settings.isSidebarCollapsed();
        double w = collapsed ? 72 : 244;
        sidebar.setMinWidth(w);
        sidebar.setPrefWidth(w);
        sidebar.setMaxWidth(w);
        sidebar.getStyleClass().remove("sidebar-collapsed");
        if (collapsed) sidebar.getStyleClass().add("sidebar-collapsed");
        sidebar.lookupAll(".nav-section-title").forEach(n -> {
            n.setVisible(!collapsed);
            n.setManaged(!collapsed);
        });
        sidebar.lookupAll(".sidebar-version").forEach(n -> {
            n.setVisible(!collapsed);
            n.setManaged(!collapsed);
        });
        navigationManager.setCollapsed(collapsed);
    }

    private HBox createStatusBar() {
        Label live = new Label("● Monitorando");
        live.getStyleClass().add("status-label-active");
        Label cpu = metric("CPU --");
        Label gpu = metric("GPU --");
        Label ram = metric("RAM --");
        Label ping = metric("PING --");
        guardStatus = metric("");
        Label version = new Label(Brand.NAME + " v" + Brand.VERSION);
        version.getStyleClass().add("status-version");

        HBox bar = new HBox(18, live, cpu, gpu, ram, ping, Ui.spacer(), guardStatus, version);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 16, 0, 16));
        bar.setMinHeight(30);

        systemInfoService.snapshotProperty().addListener((obs, old, s) -> {
            if (s == null) return;
            cpu.setText(String.format(Locale.US, "CPU %.0f%%", s.getCpuUsage()));
            gpu.setText(String.format(Locale.US, "GPU %.0f%%", s.getGpuUsage()));
            ram.setText(String.format(Locale.US, "RAM %.0f%%", s.getRamUsagePercent()));
            ping.setText(s.getNetworkPing() > 0 ? String.format(Locale.US, "PING %.0f ms", s.getNetworkPing()) : "PING --");
        });
        return bar;
    }

    private Label metric(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("status-metric");
        return l;
    }

    public void updateBoosterChip() {
        if (boosterChip == null) return;
        boosterChip.getStyleClass().removeAll("chip-ok", "chip-live", "chip-muted");
        if (gameBoosterService.isGameActive()) {
            boosterChip.setText("● BOOST ATIVO · " + gameBoosterService.activeGame());
            boosterChip.getStyleClass().add("chip-live");
        } else if (gameBoosterService.isRunning()) {
            boosterChip.setText("GAME BOOSTER PRONTO");
            boosterChip.getStyleClass().add("chip-ok");
        } else {
            boosterChip.setText("GAME BOOSTER OFF");
            boosterChip.getStyleClass().add("chip-muted");
        }
    }

    private void updateGuardStatus() {
        if (guardStatus == null) return;
        if (memoryService.isGuardRunning()) {
            guardStatus.setText("RAM Guard ativo · " + memoryService.getGuardRuns() + " limpezas · "
                    + Ui.formatBytes(memoryService.getGuardFreedTotal()));
        } else {
            guardStatus.setText("");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Window chrome
    // ═══════════════════════════════════════════════════════════════

    private void fitToScreen(Stage stage) {
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double w = Math.min(1320, screen.getWidth() - 40);
        double h = Math.min(840, screen.getHeight() - 40);
        stage.setWidth(w);
        stage.setHeight(h);
        stage.setX(screen.getMinX() + (screen.getWidth() - w) / 2);
        stage.setY(screen.getMinY() + (screen.getHeight() - h) / 2);
    }

    private void toggleMaximize() {
        appRoot().getStyleClass().remove("app-root-maximized");
        if (maximized && restoreBounds != null) {
            primaryStage.setX(restoreBounds.getMinX());
            primaryStage.setY(restoreBounds.getMinY());
            primaryStage.setWidth(restoreBounds.getWidth());
            primaryStage.setHeight(restoreBounds.getHeight());
            maximized = false;
        } else {
            restoreBounds = new Rectangle2D(primaryStage.getX(), primaryStage.getY(),
                    primaryStage.getWidth(), primaryStage.getHeight());
            Rectangle2D screen = Screen.getScreensForRectangle(restoreBounds).stream().findFirst()
                    .orElse(Screen.getPrimary()).getVisualBounds();
            primaryStage.setX(screen.getMinX());
            primaryStage.setY(screen.getMinY());
            primaryStage.setWidth(screen.getWidth());
            primaryStage.setHeight(screen.getHeight());
            maximized = true;
            appRoot().getStyleClass().add("app-root-maximized");
        }
    }

    private javafx.scene.Parent appRoot() {
        return (javafx.scene.Parent) ((StackPane) primaryStage.getScene().getRoot()).getChildren().get(0);
    }

    /** Real edge/corner resizing for the undecorated window. */
    private void enableResize(Scene scene, StackPane root) {
        final double margin = 6;
        final Cursor[] mode = {Cursor.DEFAULT};
        final double[] start = new double[6];

        scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            if (maximized) {
                scene.setCursor(Cursor.DEFAULT);
                return;
            }
            double x = e.getSceneX(), y = e.getSceneY(), w = scene.getWidth(), h = scene.getHeight();
            boolean l = x < margin, r = x > w - margin, t = y < margin, b = y > h - margin;
            Cursor c = t && l ? Cursor.NW_RESIZE : t && r ? Cursor.NE_RESIZE : b && l ? Cursor.SW_RESIZE
                    : b && r ? Cursor.SE_RESIZE : l ? Cursor.W_RESIZE : r ? Cursor.E_RESIZE
                    : t ? Cursor.N_RESIZE : b ? Cursor.S_RESIZE : Cursor.DEFAULT;
            mode[0] = c;
            scene.setCursor(c);
        });
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (mode[0] == Cursor.DEFAULT) return;
            start[0] = e.getScreenX();
            start[1] = e.getScreenY();
            start[2] = primaryStage.getX();
            start[3] = primaryStage.getY();
            start[4] = primaryStage.getWidth();
            start[5] = primaryStage.getHeight();
            e.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            Cursor c = mode[0];
            if (c == Cursor.DEFAULT) return;
            double dx = e.getScreenX() - start[0], dy = e.getScreenY() - start[1];
            double minW = primaryStage.getMinWidth(), minH = primaryStage.getMinHeight();
            if (c == Cursor.E_RESIZE || c == Cursor.NE_RESIZE || c == Cursor.SE_RESIZE) {
                primaryStage.setWidth(Math.max(minW, start[4] + dx));
            }
            if (c == Cursor.S_RESIZE || c == Cursor.SE_RESIZE || c == Cursor.SW_RESIZE) {
                primaryStage.setHeight(Math.max(minH, start[5] + dy));
            }
            if (c == Cursor.W_RESIZE || c == Cursor.NW_RESIZE || c == Cursor.SW_RESIZE) {
                double w = Math.max(minW, start[4] - dx);
                primaryStage.setX(start[2] + start[4] - w);
                primaryStage.setWidth(w);
            }
            if (c == Cursor.N_RESIZE || c == Cursor.NW_RESIZE || c == Cursor.NE_RESIZE) {
                double h = Math.max(minH, start[5] - dy);
                primaryStage.setY(start[3] + start[5] - h);
                primaryStage.setHeight(h);
            }
            e.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> mode[0] = Cursor.DEFAULT);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Overlay, tray, lifecycle
    // ═══════════════════════════════════════════════════════════════

    public void toggleOverlay() {
        if (overlayWindow == null) overlayWindow = new OverlayWindow(systemInfoService);
        overlayWindow.toggle();
    }

    public boolean isOverlayShowing() {
        return overlayWindow != null && overlayWindow.isShowing();
    }

    public void refreshOverlayVisibility() {
        if (overlayWindow != null) overlayWindow.refreshVisibility();
    }

    private boolean traySetup;

    private void handleClose() {
        if (SystemTray.isSupported() && traySetup && settings.isCloseToTray()) {
            primaryStage.hide();
            MemoryTrimmer.gcAndTrim();
        } else {
            exitApplication();
        }
    }

    private void setupSystemTray() {
        if (!SystemTray.isSupported() || traySetup) return;
        try {
            java.awt.Image image = javax.imageio.ImageIO.read(getClass().getResourceAsStream("/logo.png"));
            PopupMenu popup = new PopupMenu();
            MenuItem open = new MenuItem("Abrir " + Brand.NAME);
            open.addActionListener(e -> Platform.runLater(this::restoreWindow));
            MenuItem ram = new MenuItem("Liberar RAM agora");
            ram.addActionListener(e -> new Thread(() -> {
                MemoryService.CleanResult r = memoryService.clean(MemoryService.CleanMode.DEEP);
                Platform.runLater(() -> NotificationManager.show(
                        r.success() ? "RAM: " + Ui.formatBytes(r.freedBytes()) + " liberados" : r.message(),
                        r.success() ? NotificationManager.Type.SUCCESS : NotificationManager.Type.WARNING));
            }, "TrayRam").start());
            MenuItem exit = new MenuItem("Sair");
            exit.addActionListener(e -> exitApplication());
            popup.add(open);
            popup.add(ram);
            popup.addSeparator();
            popup.add(exit);

            TrayIcon icon = new TrayIcon(image, Brand.FULL_NAME, popup);
            icon.setImageAutoSize(true);
            icon.addActionListener(e -> Platform.runLater(this::restoreWindow));
            SystemTray.getSystemTray().add(icon);
            Platform.setImplicitExit(false);
            traySetup = true;
        } catch (Exception e) {
            System.err.println("[SystemTray] " + e.getMessage());
        }
    }

    private void restoreWindow() {
        primaryStage.show();
        primaryStage.setIconified(false);
        primaryStage.toFront();
    }

    public void exitApplication() {
        try {
            gameBoosterService.stop();
            memoryService.stopGuard();
            systemInfoService.stopMonitoring();
        } catch (Exception ignored) {}
        Platform.exit();
        System.exit(0);
    }

    private static boolean isElevated() {
        try {
            if (!Advapi32Util.isCurrentProcessElevated()) return false;
            WinNative.enableMemoryPrivileges();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Accessors for pages
    // ═══════════════════════════════════════════════════════════════

    public Stage getStage() { return primaryStage; }
    public AppSettings getSettings() { return settings; }
    public boolean isElevatedProcess() { return elevated; }
    public PowerShellService getPowerShellService() { return powerShellService; }
    public RegistryService getRegistryService() { return registryService; }
    public BackupService getBackupService() { return backupService; }
    public SystemInfoService getSystemInfoService() { return systemInfoService; }
    public ProcessService getProcessService() { return processService; }
    public NetworkService getNetworkService() { return networkService; }
    public GpuService getGpuService() { return gpuService; }
    public CpuService getCpuService() { return cpuService; }
    public StorageService getStorageService() { return storageService; }
    public MemoryService getMemoryService() { return memoryService; }
    public SteamLocator getSteamLocator() { return steamLocator; }
    public TweakService getTweakService() { return tweakService; }
    public SafetyService getSafetyService() { return safetyService; }
    public CleanupService getCleanupService() { return cleanupService; }
    public GameBoosterService getGameBoosterService() { return gameBoosterService; }
    public Cs2ConfigService getCs2ConfigService() { return cs2ConfigService; }
    public NavigationManager getNavigationManager() { return navigationManager; }

    public static void main(String[] args) {
        launch(args);
    }
}
