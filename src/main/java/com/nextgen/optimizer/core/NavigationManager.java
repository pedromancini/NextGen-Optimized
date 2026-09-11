package com.nextgen.optimizer.core;

import com.nextgen.optimizer.App;
import com.nextgen.optimizer.ui.pages.*;

import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.Map;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;

/**
 * Manages page navigation within the sidebar and content area.
 * Handles page creation, caching, and animated transitions.
 */
public class NavigationManager {

    private final StackPane contentArea;
    private final App app;
    private final Map<String, Node> pageCache = new HashMap<>();
    private final Map<String, HBox> navItems = new HashMap<>();
    private String currentPage = "";

    public NavigationManager(StackPane contentArea, App app) {
        this.contentArea = contentArea;
        this.app = app;
    }

    /**
     * Creates a styled navigation item for the sidebar.
     */
    public HBox createNavItem(String id, String iconOrPath, String label) {
        HBox item = new HBox(10);
        item.getStyleClass().add("nav-item");
        item.setAlignment(Pos.CENTER_LEFT);

        Node iconNode;
        if (iconOrPath.startsWith("/")) {
            ImageView imgView = new ImageView();
            try {
                imgView.setImage(new Image(getClass().getResourceAsStream(iconOrPath)));
                imgView.setFitWidth(28);
                imgView.setFitHeight(18);
                imgView.setPreserveRatio(true);
                imgView.setSmooth(true);
            } catch (Exception ignored) {}
            iconNode = imgView;
        } else {
            String literal = switch (id) {
                case "dashboard" -> "mdi2v-view-dashboard-outline";
                case "monitor" -> "mdi2c-chart-line";
                case "privacy" -> "mdi2s-shield-check-outline";
                case "quick-opt" -> "mdi2l-lightning-bolt";
                case "performance" -> "mdi2g-gamepad-variant-outline";
                case "network" -> "mdi2w-web";
                case "gpu" -> "mdi2e-expansion-card";
                case "cpu" -> "mdi2c-chip";
                case "ram" -> "mdi2m-memory";
                case "storage" -> "mdi2h-harddisk";
                case "bios-tips" -> "mdi2l-lightbulb-outline";
                case "overlay" -> "mdi2m-monitor";
                default -> "mdi2c-cog-outline";
            };
            FontIcon icon = new FontIcon(literal);
            icon.setIconSize(18);
            icon.getStyleClass().add("navigation-icon");
            iconNode = icon;
        }

        Label textLabel = new Label(label);
        textLabel.getStyleClass().add("nav-item-label");
        textLabel.setWrapText(true);
        textLabel.setMinWidth(0);
        HBox.setHgrow(textLabel, Priority.ALWAYS);
        item.setFocusTraversable(true);
        item.setAccessibleText(label);
        item.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
                navigateTo(id);
                e.consume();
            }
        });
        Tooltip.install(item, new Tooltip(label));

        item.getChildren().addAll(iconNode, textLabel);
        item.setOnMouseClicked(e -> navigateTo(id));

        navItems.put(id, item);
        return item;
    }

    /**
     * Navigates to the specified page with a fade transition.
     */
    public void navigateTo(String pageId) {
        if (pageId.equals(currentPage)) return;

        // Update nav item styles
        navItems.values().forEach(item -> {
            item.getStyleClass().remove("nav-item-active");
        });
        HBox activeItem = navItems.get(pageId);
        if (activeItem != null) {
            activeItem.getStyleClass().add("nav-item-active");
        }

        // Get or create page
        Node page = getOrCreatePage(pageId);
        if (page == null) return;

        // Animate transition
        Node currentContent = contentArea.getChildren().isEmpty() ? null : contentArea.getChildren().get(0);

        page.setOpacity(0);
        page.setTranslateY(8);
        contentArea.getChildren().setAll(page);
        if (contentArea.getParent() != null && contentArea.getParent().getParent() instanceof javafx.scene.control.ScrollPane scroll) {
            scroll.setVvalue(0);
        }

        // Fade in new page
        FadeTransition fadeIn = new FadeTransition(Duration.millis(250), page);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition slideIn = new TranslateTransition(Duration.millis(250), page);
        slideIn.setFromY(8);
        slideIn.setToY(0);
        slideIn.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition transition = new ParallelTransition(fadeIn, slideIn);
        transition.play();

        currentPage = pageId;
    }

    /**
     * Gets a cached page or creates a new one.
     */
    private Node getOrCreatePage(String pageId) {
        if (pageCache.containsKey(pageId)) {
            return pageCache.get(pageId);
        }

        Node page = switch (pageId) {
            case "dashboard" -> new DashboardPage(app);
            case "monitor" -> new MonitorPage(app);
            case "privacy" -> new PrivacyPage(app);
            case "quick-opt" -> new QuickOptimizationPage(app);
            case "performance", "fps-boost" -> new PerformancePage(app);
            case "network" -> new NetworkPage(app);
            case "gpu" -> new GpuPage(app);
            case "cpu" -> new CpuPage(app);
            case "ram" -> new RamPage(app);
            case "storage" -> new StoragePage(app);
            case "cs2" -> new Cs2Page(app);
            case "bios-tips" -> new BiosTipsPage(app);
            case "overlay" -> new OverlayPage(app);
            case "settings" -> new SettingsPage(app);
            default -> createPlaceholderPage(pageId);
        };

        pageCache.put(pageId, page);
        return page;
    }

    private Node createPlaceholderPage(String pageId) {
        VBox placeholder = new VBox(12);
        placeholder.getStyleClass().add("page-container");
        placeholder.setAlignment(Pos.CENTER);

        Label title = new Label("Página em construção");
        title.getStyleClass().add("page-title");

        Label sub = new Label(pageId);
        sub.getStyleClass().add("page-subtitle");

        placeholder.getChildren().addAll(title, sub);
        return placeholder;
    }

    /**
     * Refreshes the current page (re-creates it).
     */
    public void refreshCurrentPage() {
        pageCache.remove(currentPage);
        String page = currentPage;
        currentPage = "";
        navigateTo(page);
    }
}
