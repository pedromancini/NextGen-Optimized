package com.nextgen.optimizer.core;

import com.nextgen.optimizer.App;
import com.nextgen.optimizer.ui.pages.*;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Sidebar navigation: builds nav items, caches pages, animates transitions and
 * supports a collapsed (icon rail) mode.
 */
public class NavigationManager {

    public record PageDef(String id, String icon, String label) {}

    private final StackPane contentArea;
    private final ScrollPane scroll;
    private final App app;
    private final Map<String, Node> pageCache = new HashMap<>();
    private final Map<String, HBox> navItems = new LinkedHashMap<>();
    private final Map<String, PageDef> defs = new HashMap<>();
    private final List<Label> labels = new ArrayList<>();
    private String currentPage = "";
    private Consumer<PageDef> onNavigate;

    public NavigationManager(StackPane contentArea, ScrollPane scroll, App app) {
        this.contentArea = contentArea;
        this.scroll = scroll;
        this.app = app;
    }

    public void setOnNavigate(Consumer<PageDef> listener) {
        this.onNavigate = listener;
    }

    public HBox createNavItem(String id, String icon, String label) {
        PageDef def = new PageDef(id, icon, label);
        defs.put(id, def);

        FontIcon fontIcon = new FontIcon(icon);
        fontIcon.setIconSize(19);
        fontIcon.getStyleClass().add("navigation-icon");
        StackPane iconBox = new StackPane(fontIcon);
        iconBox.getStyleClass().add("nav-icon-box");

        Label text = new Label(label);
        text.getStyleClass().add("nav-item-label");
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        labels.add(text);

        HBox item = new HBox(12, iconBox, text);
        item.getStyleClass().add("nav-item");
        item.setAlignment(Pos.CENTER_LEFT);
        item.setFocusTraversable(true);
        item.setAccessibleText(label);
        Tooltip tip = new Tooltip(label);
        tip.setShowDelay(Duration.millis(250));
        Tooltip.install(item, tip);
        item.setOnMouseClicked(e -> navigateTo(id));
        item.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
                navigateTo(id);
                e.consume();
            }
        });

        navItems.put(id, item);
        return item;
    }

    public void setCollapsed(boolean collapsed) {
        for (Label l : labels) {
            l.setVisible(!collapsed);
            l.setManaged(!collapsed);
        }
        for (HBox item : navItems.values()) {
            item.setAlignment(collapsed ? Pos.CENTER : Pos.CENTER_LEFT);
        }
    }

    public String currentPage() {
        return currentPage;
    }

    public void navigateTo(String pageId) {
        if (pageId.equals(currentPage)) return;

        navItems.values().forEach(item -> item.getStyleClass().remove("nav-item-active"));
        HBox active = navItems.get(pageId);
        if (active != null) active.getStyleClass().add("nav-item-active");

        Node page = getOrCreatePage(pageId);
        page.setOpacity(0);
        page.setTranslateY(10);
        contentArea.getChildren().setAll(page);
        scroll.setVvalue(0);

        FadeTransition fade = new FadeTransition(Duration.millis(220), page);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);
        TranslateTransition slide = new TranslateTransition(Duration.millis(220), page);
        slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(fade, slide).play();

        currentPage = pageId;
        PageDef def = defs.get(pageId);
        if (onNavigate != null && def != null) onNavigate.accept(def);
    }

    private Node getOrCreatePage(String pageId) {
        Node cached = pageCache.get(pageId);
        if (cached != null) return cached;
        Node page = switch (pageId) {
            case "dashboard" -> new DashboardPage(app);
            case "full-opt" -> new FullOptimizationPage(app);
            case "monitor" -> new MonitorPage(app);
            case "tweaks" -> new TweaksPage(app);
            case "cs2" -> new Cs2Page(app);
            case "ram" -> new RamPage(app);
            case "cleanup" -> new CleanupPage(app);
            case "startup" -> new StartupPage(app);
            case "network" -> new NetworkPage(app);
            case "gpu" -> new GpuPage(app);
            case "cpu" -> new CpuPage(app);
            case "storage" -> new StoragePage(app);
            case "privacy" -> new PrivacyPage(app);
            case "overlay" -> new OverlayPage(app);
            case "bios-tips" -> new BiosTipsPage(app);
            case "settings" -> new SettingsPage(app);
            default -> placeholder(pageId);
        };
        pageCache.put(pageId, page);
        return page;
    }

    private Node placeholder(String pageId) {
        Label title = new Label("Página não encontrada: " + pageId);
        title.getStyleClass().add("page-title");
        VBox box = new VBox(title);
        box.getStyleClass().add("page-container");
        return box;
    }

    /** Re-creates the current page (used after global state changes). */
    public void refreshCurrentPage() {
        pageCache.remove(currentPage);
        String page = currentPage;
        currentPage = "";
        navigateTo(page);
    }

    /** Drops cached pages so they re-read state next time they are opened. */
    public void invalidate(String... pageIds) {
        for (String id : pageIds) {
            if (!id.equals(currentPage)) pageCache.remove(id);
        }
    }
}
