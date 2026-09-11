package com.nextgen.optimizer;

import com.nextgen.optimizer.core.NavigationManager;
import com.nextgen.optimizer.services.*;
import com.nextgen.optimizer.overlay.OverlayWindow;
import com.nextgen.optimizer.ui.pages.*;

import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.animation.*;
import javafx.scene.image.Image;
import javafx.util.Duration;

/**
 * NextGen Optimized — Main Application
 * Premium PC optimization tool built with JavaFX.
 */
public class App extends Application {

    private Stage primaryStage;
    private double xOffset = 0;
    private double yOffset = 0;
    private boolean isMaximized = false;
    private double prevX, prevY, prevW, prevH;

    // Services
    private PowerShellService powerShellService;
    private RegistryService registryService;
    private BackupService backupService;
    private SystemInfoService systemInfoService;
    private PerformanceService performanceService;
    private FpsBoostService fpsBoostService;
    private NetworkService networkService;
    private GpuService gpuService;
    private CpuService cpuService;
    private RamService ramService;
    private StorageService storageService;

    // UI
    private NavigationManager navigationManager;
    private StackPane contentArea;
    private Label statusLabel;
    private OverlayWindow overlayWindow;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.initStyle(StageStyle.TRANSPARENT);

        initializeServices();

        Scene scene = new Scene(createRootLayout(), 1280, 800);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
        scene.getStylesheets().add(getClass().getResource("/css/components.css").toExternalForm());
        scene.getStylesheets().add(getClass().getResource("/css/workspace.css").toExternalForm());

        stage.setScene(scene);
        stage.setTitle("NextGen Optimized");
        try {
            stage.getIcons().add(new Image(getClass().getResourceAsStream("/logo.png")));
        } catch (Exception ignored) {}
        stage.setMinWidth(1000);
        stage.setMinHeight(650);

        // Fade-in on launch
        scene.getRoot().setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(600), scene.getRoot());
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.setInterpolator(Interpolator.EASE_OUT);

        setupSystemTray();
        stage.show();
        fadeIn.play();

        // Navigate to dashboard
        navigationManager.navigateTo("dashboard");
    }

    private void initializeServices() {
        powerShellService = new PowerShellService();
        registryService = new RegistryService();
        backupService = new BackupService();
        systemInfoService = new SystemInfoService();
        performanceService = new PerformanceService(powerShellService, registryService, backupService);
        fpsBoostService = new FpsBoostService(registryService, backupService);
        networkService = new NetworkService(powerShellService, registryService, backupService);
        gpuService = new GpuService(powerShellService, systemInfoService);
        cpuService = new CpuService(powerShellService, registryService, backupService, systemInfoService);
        ramService = new RamService(powerShellService, systemInfoService);
        storageService = new StorageService(powerShellService, systemInfoService);

        // Start monitoring
        systemInfoService.startMonitoring();
    }

    private BorderPane createRootLayout() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");

        // Custom titlebar
        HBox titleBar = createTitleBar();
        root.setTop(titleBar);

        // Content area (must be initialized before createSidebar sets up NavigationManager)
        contentArea = new StackPane();
        contentArea.setAlignment(Pos.TOP_LEFT);
        contentArea.getStyleClass().add("content-area");

        // Sidebar
        VBox sidebar = createSidebar();
        root.setLeft(sidebar);

        ScrollPane scrollPane = new ScrollPane(contentArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);
        scrollPane.getStyleClass().add("content-scroll");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        root.setCenter(scrollPane);

        // Status bar
        HBox statusBar = createStatusBar();
        root.setBottom(statusBar);

        // Resize border
        enableResize(root);

        return root;
    }

    private HBox createTitleBar() {
        HBox titleBar = new HBox();
        titleBar.getStyleClass().add("title-bar");
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(0, 8, 0, 16));
        titleBar.setPrefHeight(40);

        // App icon and title
        Label icon = new Label("⚡");
        icon.getStyleClass().add("title-bar-icon");

        Label title = new Label("NEXTGEN");
        title.getStyleClass().add("title-bar-text");

        Label titleAccent = new Label(" OPTIMIZED");
        titleAccent.getStyleClass().add("title-bar-text-accent");

        HBox titleGroup = new HBox(4, icon, title, titleAccent);
        titleGroup.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleGroup, Priority.ALWAYS);

        // Window controls
        HBox windowControls = new HBox(2);
        windowControls.setAlignment(Pos.CENTER_RIGHT);

        Label minimizeBtn = createWindowButton("─", "window-btn");
        minimizeBtn.setOnMouseClicked(e -> primaryStage.setIconified(true));

        Label maximizeBtn = createWindowButton("□", "window-btn");
        maximizeBtn.setOnMouseClicked(e -> toggleMaximize());

        Label closeBtn = createWindowButton("✕", "window-btn-close");
        closeBtn.setOnMouseClicked(e -> handleClose());

        windowControls.getChildren().addAll(minimizeBtn, maximizeBtn, closeBtn);
        titleBar.getChildren().addAll(titleGroup, windowControls);

        // Drag support
        titleBar.setOnMousePressed(e -> {
            xOffset = e.getSceneX();
            yOffset = e.getSceneY();
        });
        titleBar.setOnMouseDragged(e -> {
            if (!isMaximized) {
                primaryStage.setX(e.getScreenX() - xOffset);
                primaryStage.setY(e.getScreenY() - yOffset);
            }
        });
        titleBar.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) toggleMaximize();
        });

        return titleBar;
    }

    private Label createWindowButton(String text, String styleClass) {
        Label btn = new Label(text);
        btn.getStyleClass().addAll("window-btn-base", styleClass);
        btn.setPrefSize(36, 28);
        btn.setAlignment(Pos.CENTER);
        return btn;
    }

    private void toggleMaximize() {
        if (isMaximized) {
            primaryStage.setX(prevX);
            primaryStage.setY(prevY);
            primaryStage.setWidth(prevW);
            primaryStage.setHeight(prevH);
            isMaximized = false;
        } else {
            prevX = primaryStage.getX();
            prevY = primaryStage.getY();
            prevW = primaryStage.getWidth();
            prevH = primaryStage.getHeight();
            var screen = javafx.stage.Screen.getPrimary().getVisualBounds();
            primaryStage.setX(screen.getMinX());
            primaryStage.setY(screen.getMinY());
            primaryStage.setWidth(screen.getWidth());
            primaryStage.setHeight(screen.getHeight());
            isMaximized = true;
        }
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(4);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(12, 8, 12, 8));
        sidebar.setPrefWidth(238);
        sidebar.setMinWidth(238);

        // Logo area
        VBox logoArea = new VBox(2);
        logoArea.setAlignment(Pos.CENTER);
        logoArea.setPadding(new Insets(4, 0, 10, 0));

        Label logoIcon = new Label("⚡");
        logoIcon.getStyleClass().add("sidebar-logo-icon");

        Label logoText = new Label("NextGen");
        logoText.getStyleClass().add("sidebar-logo-text");

        Label versionLabel = new Label("Privacidade • revisão 2");
        versionLabel.getStyleClass().add("sidebar-version");

        logoArea.getChildren().addAll(logoText, versionLabel);

        // Separator
        Region sep1 = new Region();
        sep1.getStyleClass().add("sidebar-separator");

        // Navigation items
        navigationManager = new NavigationManager(contentArea, this);

        VBox navItems = new VBox(2);
        navItems.getChildren().addAll(
            createNavSection("PRINCIPAL"),
            navigationManager.createNavItem("dashboard", "📊", "Visão geral"),
            navigationManager.createNavItem("monitor", "📈", "Uso e processos"),
            navigationManager.createNavItem("privacy", "", "Privacidade Windows"),
            createNavSection("OTIMIZAÇÃO"),
            navigationManager.createNavItem("quick-opt", "⚡", "Otimizações Rápidas"),
            navigationManager.createNavItem("performance", "🎮", "Desempenho e FPS"),
            navigationManager.createNavItem("cs2", "/cs2_logo.png", "CS2 Competitivo"),
            navigationManager.createNavItem("network", "🌐", "Rede & Ping"),
            createNavSection("HARDWARE"),
            navigationManager.createNavItem("gpu", "💻", "GPU"),
            navigationManager.createNavItem("cpu", "🧠", "CPU"),
            navigationManager.createNavItem("ram", "📦", "RAM"),
            navigationManager.createNavItem("storage", "💿", "SSD / HD"),
            createNavSection("GUIAS & DICAS"),
            navigationManager.createNavItem("bios-tips", "💡", "Dicas BIOS & HW"),
            createNavSection("SISTEMA"),
            navigationManager.createNavItem("overlay", "🖥️", "Overlay OSD"),
            navigationManager.createNavItem("settings", "⚙️", "Configurações")
        );

        ScrollPane navScroll = new ScrollPane(navItems);
        navScroll.getStyleClass().add("content-scroll");
        navScroll.setFitToWidth(true);
        navScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");
        navScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        navScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(navScroll, Priority.ALWAYS);

        sidebar.getChildren().addAll(logoArea, sep1, navScroll);
        return sidebar;
    }

    private Label createNavSection(String title) {
        Label section = new Label(title);
        section.getStyleClass().add("nav-section-title");
        VBox.setMargin(section, new Insets(16, 0, 4, 12));
        return section;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(16);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(4, 16, 4, 16));
        statusBar.setPrefHeight(28);

        statusLabel = new Label("● Sistema monitorando");
        statusLabel.getStyleClass().add("status-label-active");

        Label cpuStatus = new Label("CPU: ---%");
        cpuStatus.getStyleClass().add("status-metric");

        Label ramStatus = new Label("RAM: ---%");
        ramStatus.getStyleClass().add("status-metric");

        Label gpuStatus = new Label("GPU: ---%");
        gpuStatus.getStyleClass().add("status-metric");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label versionStatus = new Label("NextGen • Privacidade r2");
        versionStatus.getStyleClass().add("status-version");

        statusBar.getChildren().addAll(statusLabel, cpuStatus, ramStatus, gpuStatus, spacer, versionStatus);

        // Update status bar with real data
        if (systemInfoService != null) {
            systemInfoService.snapshotProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    Platform.runLater(() -> {
                        cpuStatus.setText(String.format("CPU: %.0f%%", newVal.getCpuUsage()));
                        ramStatus.setText(String.format("RAM: %.0f%%", newVal.getRamUsagePercent()));
                        gpuStatus.setText(String.format("GPU: %.0f%%", newVal.getGpuUsage()));
                    });
                }
            });
        }

        return statusBar;
    }

    private void enableResize(BorderPane root) {
        final int RESIZE_MARGIN = 6;
        root.setOnMouseMoved(e -> {
            double x = e.getX(), y = e.getY();
            double w = root.getWidth(), h = root.getHeight();
            if (x < RESIZE_MARGIN && y < RESIZE_MARGIN) {
                root.setCursor(javafx.scene.Cursor.NW_RESIZE);
            } else if (x > w - RESIZE_MARGIN && y < RESIZE_MARGIN) {
                root.setCursor(javafx.scene.Cursor.NE_RESIZE);
            } else if (x < RESIZE_MARGIN && y > h - RESIZE_MARGIN) {
                root.setCursor(javafx.scene.Cursor.SW_RESIZE);
            } else if (x > w - RESIZE_MARGIN && y > h - RESIZE_MARGIN) {
                root.setCursor(javafx.scene.Cursor.SE_RESIZE);
            } else if (x < RESIZE_MARGIN) {
                root.setCursor(javafx.scene.Cursor.W_RESIZE);
            } else if (x > w - RESIZE_MARGIN) {
                root.setCursor(javafx.scene.Cursor.E_RESIZE);
            } else if (y < RESIZE_MARGIN) {
                root.setCursor(javafx.scene.Cursor.N_RESIZE);
            } else if (y > h - RESIZE_MARGIN) {
                root.setCursor(javafx.scene.Cursor.S_RESIZE);
            } else {
                root.setCursor(javafx.scene.Cursor.DEFAULT);
            }
        });
    }

    public void toggleOverlay() {
        if (overlayWindow == null) {
            overlayWindow = new OverlayWindow(systemInfoService);
        }
        overlayWindow.toggle();
    }

    public boolean isOverlayShowing() {
        return overlayWindow != null && overlayWindow.isShowing();
    }

    public void refreshOverlayVisibility() {
        if (overlayWindow != null) {
            overlayWindow.refreshVisibility();
        }
    }

    private boolean isTraySetup = false;

    private void handleClose() {
        if (SystemTray.isSupported()) {
            primaryStage.hide();
            com.nextgen.optimizer.core.NotificationManager.show("NextGen rodando em segundo plano na bandeja do Windows!", com.nextgen.optimizer.core.NotificationManager.Type.INFO);
            trimWorkingSetMemory();
        } else {
            exitApplication();
        }
    }

    private void trimWorkingSetMemory() {
        com.nextgen.optimizer.core.MemoryTrimmer.gcAndTrim();
    }

    private void setupSystemTray() {
        if (!SystemTray.isSupported() || isTraySetup) {
            return;
        }
        isTraySetup = true;
        Platform.setImplicitExit(false);
        try {
            SystemTray tray = SystemTray.getSystemTray();

            java.awt.Image image = null;
            try {
                java.io.InputStream is = getClass().getResourceAsStream("/logo.png");
                if (is != null) {
                    image = javax.imageio.ImageIO.read(is);
                }
            } catch (Exception ignored) {}

            if (image == null) {
                BufferedImage bi = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = bi.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new java.awt.Color(59, 130, 246));
                g.fillOval(1, 1, 14, 14);
                g.setColor(java.awt.Color.WHITE);
                g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 10));
                g.drawString("N", 4, 12);
                g.dispose();
                image = bi;
            }

            PopupMenu popup = new PopupMenu();

            MenuItem openItem = new MenuItem("Abrir NextGen Optimized");
            openItem.addActionListener(e -> Platform.runLater(() -> restoreWindow()));

            MenuItem ramItem = new MenuItem("Otimizar RAM Agora");
            ramItem.addActionListener(e -> {
                if (ramService != null) {
                    ramService.clearStandbyMemory();
                }
            });

            MenuItem exitItem = new MenuItem("Sair Definitivamente");
            exitItem.addActionListener(e -> exitApplication());

            popup.add(openItem);
            popup.add(ramItem);
            popup.addSeparator();
            popup.add(exitItem);

            TrayIcon trayIcon = new TrayIcon(image, "NextGen Optimized — Otimizador em Segundo Plano", popup);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> Platform.runLater(() -> restoreWindow()));

            tray.add(trayIcon);
        } catch (Exception e) {
            System.err.println("[SystemTray] Erro ao configurar bandeja: " + e.getMessage());
        }
    }

    private void restoreWindow() {
        if (primaryStage != null) {
            primaryStage.show();
            primaryStage.setIconified(false);
            primaryStage.toFront();
        }
    }

    public void exitApplication() {
        if (systemInfoService != null) {
            systemInfoService.stopMonitoring();
        }
        Platform.exit();
        System.exit(0);
    }

    // Service getters for pages
    public PowerShellService getPowerShellService() { return powerShellService; }
    public RegistryService getRegistryService() { return registryService; }
    public BackupService getBackupService() { return backupService; }
    public SystemInfoService getSystemInfoService() { return systemInfoService; }
    public PerformanceService getPerformanceService() { return performanceService; }
    public FpsBoostService getFpsBoostService() { return fpsBoostService; }
    public NetworkService getNetworkService() { return networkService; }
    public GpuService getGpuService() { return gpuService; }
    public CpuService getCpuService() { return cpuService; }
    public RamService getRamService() { return ramService; }
    public StorageService getStorageService() { return storageService; }
    public NavigationManager getNavigationManager() { return navigationManager; }

    public static void main(String[] args) {
        launch(args);
    }
}
