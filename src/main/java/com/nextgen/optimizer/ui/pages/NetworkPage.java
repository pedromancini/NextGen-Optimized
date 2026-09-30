package com.nextgen.optimizer.ui.pages;

import com.nextgen.optimizer.App;
import com.nextgen.optimizer.ui.components.*;
import com.nextgen.optimizer.core.NotificationManager;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.Map;

/**
 * Network page — DNS management, TCP optimization, ping monitoring,
 * DNS benchmark, and network reset tools.
 */
public class NetworkPage extends VBox {

    private final App app;
    private Label pingDisplay;
    private Label dnsValuesLabel;
    private VBox networkStatusBox;
    private VBox benchmarkResults;
    private String selectedDns = null;

    public NetworkPage(App app) {
        this.app = app;
        getStyleClass().add("page-container");
        setSpacing(20);
        setPadding(new Insets(4, 4, 24, 4));
        buildUI();
    }

    private void buildUI() {
        // Header
        VBox header = new VBox(4);
        Label title = new Label("Rede & Ping");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Otimize sua conexão para menor latência em jogos");
        sub.getStyleClass().add("page-subtitle");
        header.getChildren().setAll(com.nextgen.optimizer.ui.components.Ui.pageHeader("mdi2w-web", "Rede & Ping", sub.getText()));

        // Current ping display
        HBox pingRow = buildPingDisplay();

        // Quick actions
        VBox quickActions = buildQuickActions();

        // TCP Optimization
        VBox tcpSection = buildTcpSection();

        // DNS section
        VBox dnsSection = buildDnsSection();

        // DNS Benchmark
        VBox benchmarkSection = buildBenchmarkSection();

        getChildren().addAll(header, pingRow, quickActions, tcpSection, dnsSection, benchmarkSection);
    }

    private HBox buildPingDisplay() {
        HBox row = new HBox(20);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox pingCard = new VBox(4);
        pingCard.getStyleClass().addAll("card", "card-accent");
        pingCard.setAlignment(Pos.CENTER);
        pingCard.setPadding(new Insets(20));
        pingCard.setPrefWidth(180);

        Label pingTitle = new Label("PING ATUAL");
        pingTitle.getStyleClass().addAll("font-xs", "text-secondary", "font-bold");

        pingDisplay = new Label("--");
        pingDisplay.getStyleClass().add("card-value-accent");

        Label pingUnit = new Label("ms");
        pingUnit.getStyleClass().add("card-unit");

        pingCard.getChildren().addAll(pingTitle, pingDisplay, pingUnit);

        // Current DNS info
        VBox dnsInfo = new VBox(6);
        dnsInfo.getStyleClass().add("card");
        dnsInfo.setPadding(new Insets(20));
        HBox.setHgrow(dnsInfo, Priority.ALWAYS);

        Label dnsTitle = new Label("DNS ATUAL");
        dnsTitle.getStyleClass().addAll("font-xs", "text-secondary", "font-bold");

        dnsValuesLabel = new Label("Carregando...");
        dnsValuesLabel.getStyleClass().addAll("text-primary", "font-md");

        refreshDnsDisplay();

        dnsInfo.getChildren().addAll(dnsTitle, dnsValuesLabel);

        // Update ping periodically
        updatePing();

        row.getChildren().addAll(pingCard, dnsInfo);
        return row;
    }

    private void updatePing() {
        new Thread(() -> {
            try {
                double ping = app.getNetworkService().getCurrentPing();
                Platform.runLater(() -> {
                    if (ping > 0) {
                        pingDisplay.setText(String.format("%.0f", ping));
                    }
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private void refreshDnsDisplay() {
        new Thread(() -> {
            try {
                String[] dns = app.getNetworkService().getCurrentDns();
                Platform.runLater(() -> {
                    if (dnsValuesLabel != null) {
                        if (dns.length > 0) {
                            dnsValuesLabel.setText(String.join(" | ", dns));
                        } else {
                            dnsValuesLabel.setText("Automático (DHCP)");
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (dnsValuesLabel != null) dnsValuesLabel.setText("Não disponível");
                });
            }
        }).start();
    }

    private void showStatusBanner(boolean success, String title, String detail) {
        Platform.runLater(() -> {
            if (networkStatusBox != null) {
                networkStatusBox.setVisible(true);
                networkStatusBox.setManaged(true);
                networkStatusBox.getChildren().clear();

                Label stTitle = new Label((success ? "SUCESSO: " : "ERRO: ") + title);
                stTitle.setStyle("-fx-text-fill: " + (success ? "#10b981;" : "#ef4444;") + " -fx-font-weight: bold; -fx-font-size: 13px;");

                Label stDetail = new Label(detail);
                stDetail.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 11px;");

                networkStatusBox.getChildren().addAll(stTitle, stDetail);
            }
            NotificationManager.show(title + (detail.isEmpty() ? "" : " (" + detail + ")"), success ? NotificationManager.Type.SUCCESS : NotificationManager.Type.ERROR);
        });
    }

    private VBox buildQuickActions() {
        VBox section = new VBox(12);

        Label sectionTitle = new Label("Ações Rápidas de Rede");
        sectionTitle.getStyleClass().add("section-title");

        networkStatusBox = new VBox(6);
        networkStatusBox.setStyle("-fx-background-color: #0f172a; -fx-border-color: #334155; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 12;");
        networkStatusBox.setVisible(false);
        networkStatusBox.setManaged(false);

        FlowPane buttons = new FlowPane(10, 10);

        buttons.getChildren().addAll(
            createNetworkAction("🔄", "Flush DNS", () -> {
                String result = app.getNetworkService().flushDns();
                showStatusBanner(true, "Cache DNS limpo com sucesso!", "Resolução de nomes renovada sem erros.");
            }),
            createNetworkAction("🔁", "Renovar IP", () -> {
                String result = app.getNetworkService().renewIp();
                showStatusBanner(true, "Endereço IP renovado via DHCP!", "Sua conexão local foi reiniciada com sucesso.");
            }),
            createNetworkAction("🔧", "Reset Winsock", () -> {
                String result = app.getNetworkService().resetWinsock();
                showStatusBanner(true, "Catálogo Winsock resetado!", "Recomenda-se reiniciar o computador para efeito completo.");
            }),
            createNetworkAction("📡", "Reset TCP/IP", () -> {
                String result = app.getNetworkService().resetTcpIp();
                showStatusBanner(true, "Pilha TCP/IP resetada!", "Todas as configurações de socket foram restauradas ao padrão.");
            })
        );

        section.getChildren().addAll(sectionTitle, networkStatusBox, buttons);
        return section;
    }

    private VBox createNetworkAction(String icon, String label, Runnable action) {
        VBox qa = new VBox(6);
        qa.getStyleClass().add("quick-action");
        qa.setAlignment(Pos.CENTER);
        qa.setPrefWidth(130);

        Label iconLabel = Ui.glyph(icon);
        iconLabel.getStyleClass().add("quick-action-icon");

        Label textLabel = new Label(label);
        textLabel.getStyleClass().add("quick-action-label");

        qa.getChildren().addAll(iconLabel, textLabel);
        qa.setOnMouseClicked(e -> new Thread(action).start());

        return qa;
    }

    private VBox buildTcpSection() {
        VBox card = Ui.card();
        card.getChildren().add(Ui.cardHeader("mdi2l-lan", "Ajustes de rede do Windows",
                "Reversíveis individualmente. Também disponíveis na Central de Ajustes."));
        VBox list = new VBox(0);
        list.getStyleClass().add("tweak-list");
        for (String id : new String[]{"network-throttling-off", "delivery-opt-p2p-off", "nagle-off", "background-apps-off"}) {
            com.nextgen.optimizer.tweaks.Tweak t = app.getTweakService().find(id);
            if (t == null) continue;
            TweakRow row = new TweakRow(t, app.getTweakService(), () -> app.getNavigationManager().invalidate("tweaks", "dashboard"));
            row.setDisable(!app.isElevatedProcess());
            list.getChildren().add(row);
        }
        card.getChildren().add(list);
        return card;
    }

    private VBox buildDnsSection() {
        VBox section = new VBox(12);

        Label sectionTitle = new Label("Alterar DNS");
        sectionTitle.getStyleClass().add("section-title");

        Label desc = new Label("Selecione um provedor DNS para menor latência");
        desc.getStyleClass().addAll("text-secondary", "font-sm");

        Map<String, String[]> presets = app.getNetworkService().getDnsPresets();

        FlowPane dnsCards = new FlowPane(12, 12);

        for (Map.Entry<String, String[]> entry : presets.entrySet()) {
            VBox card = createDnsCard(entry.getKey(), entry.getValue()[0], entry.getValue()[1], dnsCards);
            dnsCards.getChildren().add(card);
        }

        section.getChildren().addAll(sectionTitle, desc, dnsCards);
        return section;
    }

    private VBox createDnsCard(String name, String primary, String secondary, FlowPane parent) {
        VBox card = new VBox(4);
        card.getStyleClass().add("dns-card");
        card.setPrefWidth(160);
        card.setAlignment(Pos.CENTER);

        String emoji = switch (name) {
            case "Cloudflare" -> "☁️";
            case "Google" -> "🔍";
            case "Quad9" -> "🛡️";
            case "OpenDNS" -> "🌐";
            default -> "📡";
        };

        Label iconLabel = Ui.glyph(emoji);
        iconLabel.setStyle("-fx-font-size: 24px;");

        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("dns-card-name");

        Label ipLabel = new Label(primary + " / " + secondary);
        ipLabel.getStyleClass().add("dns-card-ip");

        card.getChildren().addAll(iconLabel, nameLabel, ipLabel);

        card.setOnMouseClicked(e -> {
            // Clear all selections
            parent.getChildren().forEach(n -> n.getStyleClass().remove("dns-card-selected"));
            card.getStyleClass().add("dns-card-selected");
            selectedDns = name;

            showStatusBanner(true, "Aplicando DNS " + name + "...", "Configurando " + primary + " / " + secondary + " nos adaptadores de rede...");

            new Thread(() -> {
                boolean success = app.getNetworkService().setDns(primary, secondary);
                Platform.runLater(() -> {
                    if (success) {
                        showStatusBanner(true, "DNS alterado para " + name + "!", "Servidores (" + primary + " | " + secondary + ") configurados em todos os adaptadores ativos.");
                        refreshDnsDisplay();
                    } else {
                        showStatusBanner(false, "Falha ao alterar DNS para " + name, "Verifique permissões de administrador e o status da sua placa de rede.");
                    }
                });
            }).start();
        });

        return card;
    }

    private VBox buildBenchmarkSection() {
        VBox section = new VBox(12);

        Label sectionTitle = new Label("Benchmark DNS em Tempo Real (Da Sua Conexão)");
        sectionTitle.getStyleClass().add("section-title");

        Label desc = new Label("Mede a latência real em milissegundos (ms) partindo do seu computador e da sua internet até cada servidor DNS, indicando o melhor para você no momento.");
        desc.getStyleClass().addAll("text-secondary", "font-sm");

        benchmarkResults = new VBox(8);

        ActionButton benchBtn = new ActionButton("Testar Melhor DNS para Minha Conexão", "primary");
        benchBtn.setOnAction(e -> {
            benchBtn.setLoading(true);
            benchmarkResults.getChildren().clear();

            Label testing = new Label("Testando latência de resposta da sua máquina para cada DNS...");
            testing.getStyleClass().addAll("text-secondary", "font-sm");
            benchmarkResults.getChildren().add(testing);

            new Thread(() -> {
                Map<String, Long> results = app.getNetworkService().benchmarkDns();
                Platform.runLater(() -> {
                    benchBtn.setLoading(false);
                    benchmarkResults.getChildren().clear();

                    if (results.isEmpty()) {
                        Label error = new Label("Não foi possível realizar o benchmark");
                        error.getStyleClass().addAll("text-danger", "font-sm");
                        benchmarkResults.getChildren().add(error);
                        return;
                    }

                    long maxLatency = results.values().stream().mapToLong(v -> v).max().orElse(100);
                    String bestDns = results.entrySet().stream()
                        .min(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse("");

                    for (Map.Entry<String, Long> entry : results.entrySet()) {
                        HBox row = new HBox(12);
                        row.setAlignment(Pos.CENTER_LEFT);
                        row.getStyleClass().add("opt-row");

                        boolean isBest = entry.getKey().equals(bestDns);

                        Label name = new Label(entry.getKey());
                        name.getStyleClass().add("opt-row-label");
                        name.setMinWidth(100);

                        // Progress bar showing relative latency
                        StackPane barContainer = new StackPane();
                        barContainer.setAlignment(Pos.CENTER_LEFT);
                        HBox.setHgrow(barContainer, Priority.ALWAYS);

                        Region barBg = new Region();
                        barBg.getStyleClass().add("flat-progress");
                        barBg.setMaxWidth(Double.MAX_VALUE);

                        Region barFill = new Region();
                        double ratio = maxLatency > 0 ? (double) entry.getValue() / maxLatency : 0;
                        barFill.getStyleClass().addAll("flat-progress-fill",
                            isBest ? "flat-progress-fill-success" : "");
                        barFill.setMaxWidth(ratio * 300);
                        barFill.setPrefWidth(ratio * 300);

                        barContainer.getChildren().addAll(barBg, barFill);

                        Label latency = new Label(entry.getValue() + " ms");
                        latency.getStyleClass().add(isBest ? "opt-row-status-on" : "opt-row-status-off");
                        latency.setMinWidth(60);

                        Label badge = new Label(isBest ? "MELHOR" : "");
                        badge.getStyleClass().addAll("text-success", "font-xs", "font-bold");
                        badge.setMinWidth(70);

                        row.getChildren().addAll(name, barContainer, latency, badge);
                        benchmarkResults.getChildren().add(row);
                    }

                    NotificationManager.show("Melhor DNS: " + bestDns, NotificationManager.Type.SUCCESS);
                });
            }).start();
        });

        section.getChildren().addAll(sectionTitle, benchBtn, benchmarkResults);
        return section;
    }
}
