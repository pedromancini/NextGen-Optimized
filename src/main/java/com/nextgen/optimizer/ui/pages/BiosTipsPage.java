package com.nextgen.optimizer.ui.pages;

import com.nextgen.optimizer.App;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

/**
 * BIOS & Hardware Tips Page — Manual optimizations that cannot be done
 * via operating system commands (RAM XMP/EXPO, ReBAR, C-States, Turbo Boost).
 */
public class BiosTipsPage extends VBox {

    private final App app;

    public BiosTipsPage(App app) {
        this.app = app;
        getStyleClass().add("page-container");
        setSpacing(20);
        setPadding(new Insets(4, 4, 28, 4));
        buildUI();
    }

    private void buildUI() {
        // Header
        VBox header = new VBox(4);
        Label title = new Label("💡 Dicas de BIOS & Hardware Competitivo");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Otimizações físicas de Placa-Mãe, Memória e BIOS que não podem ser feitas por comandos do Windows");
        sub.getStyleClass().add("page-subtitle");
        header.getChildren().addAll(title, sub);

        // Section 1: BIOS (Maior Impacto)
        VBox biosCard = buildBiosImpactCard();

        // Section 2: Monitor & Periféricos
        VBox monitorCard = buildMonitorTipsCard();

        // Section 3: Energia & Placa de Vídeo
        VBox powerHardwareCard = buildPowerHardwareCard();

        getChildren().addAll(header, biosCard, monitorCard, powerHardwareCard);
    }

    private VBox buildBiosImpactCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(20));

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("🔥");
        icon.setStyle("-fx-font-size: 24px;");
        VBox textBox = new VBox(2);
        Label cardTitle = new Label("1. Configurações de BIOS / UEFI (Maior Impacto no Desempenho)");
        cardTitle.setStyle("-fx-font-weight: 800; -fx-font-size: 16px; -fx-text-fill: #f59e0b;");
        Label cardSub = new Label("Acesse a BIOS pressionando DEL ou F2 ao ligar o computador e verifique as seguintes opções:");
        cardSub.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
        textBox.getChildren().addAll(cardTitle, cardSub);
        top.getChildren().addAll(icon, textBox);

        VBox list = new VBox(12);
        list.getChildren().addAll(
            createTipItem("✅ Ative XMP / DOCP / EXPO da Memória RAM",
                "Sem o perfil XMP/EXPO ativado, memórias de 3200MHz, 3600MHz ou 6000MHz rodam na frequência padrão baixa (2133MHz / 4800MHz). Ativar o perfil libera 100% da velocidade comprada."),
            createTipItem("✅ Ative Resizable BAR (ReBAR / Smart Access Memory)",
                "Permite que o processador acesse toda a memória VRAM da placa de vídeo de uma só vez, gerando ganhos gratuitos de 5% a 15% de FPS em jogos modernos e CS2."),
            createTipItem("✅ Atualize a BIOS para uma versão estável recente",
                "Atualizações de AGESA (AMD) ou Microcódigo (Intel) corrigem instabilidades de latência e aumentam a compatibilidade com frequências altas de memória."),
            createTipItem("✅ Desative C-States Profundos (C-State Control / C1E)",
                "Opcional para modo competitivo: impede que os núcleos do processador entrem em sono profundo entre rodadas, reduzindo micro-atrasos (stuttering) e Input Lag."),
            createTipItem("✅ Ative Precision Boost Overdrive (AMD PBO) ou Turbo Boost (Intel)",
                "Garante que o processador atinja o clock máximo de boost de fábrica durante jogos em tempo real.")
        );

        card.getChildren().addAll(top, list);
        return card;
    }

    private VBox buildMonitorTipsCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(20));

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("🖥️");
        icon.setStyle("-fx-font-size: 24px;");
        VBox textBox = new VBox(2);
        Label cardTitle = new Label("2. Monitor & Mouse Competitivo");
        cardTitle.setStyle("-fx-font-weight: 800; -fx-font-size: 15px; -fx-text-fill: white;");
        Label cardSub = new Label("Verifique se seus periféricos estão configurados na taxa correta");
        cardSub.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
        textBox.getChildren().addAll(cardTitle, cardSub);
        top.getChildren().addAll(icon, textBox);

        VBox list = new VBox(12);
        list.getChildren().addAll(
            createTipItem("✅ Verifique a Taxa de Atualização (Hz) do Monitor no Windows",
                "Muitos monitores de 144Hz, 240Hz ou 360Hz vêm configurados em 60Hz de fábrica. Verifique em Configurações > Tela > Exibição Avançada."),
            createTipItem("✅ Polling Rate do Mouse em 1000Hz (ou superior)",
                "No software do seu mouse (Logitech, Razer, Zowie, etc.), certifique-se de que a taxa de relatório (Polling Rate) está em no mínimo 1000Hz (1ms).")
        );

        card.getChildren().addAll(top, list);
        return card;
    }

    private VBox buildPowerHardwareCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(20));

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("🔌");
        icon.setStyle("-fx-font-size: 24px;");
        VBox textBox = new VBox(2);
        Label cardTitle = new Label("3. Conexão Física de Placa de Vídeo & Energia");
        cardTitle.setStyle("-fx-font-weight: 800; -fx-font-size: 15px; -fx-text-fill: white;");
        Label cardSub = new Label("Boas práticas na instalação física do setup");
        cardSub.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
        textBox.getChildren().addAll(cardTitle, cardSub);
        top.getChildren().addAll(icon, textBox);

        VBox list = new VBox(12);
        list.getChildren().addAll(
            createTipItem("✅ Conecte o cabo DisplayPort diretamente na Placa de Vídeo Dedicada",
                "Nunca conecte o monitor nas portas HDMI/DisplayPort da Placa-Mãe se possuir uma GPU dedicada."),
            createTipItem("✅ Cabos de Alimentação PCIe Individuais",
                "Para placas potentes (RTX 3070/4070 ou superior), utilize cabos PCIe dedicados da fonte em vez de usar cabos em 'Y' (daisy chain).")
        );

        card.getChildren().addAll(top, list);
        return card;
    }

    private VBox createTipItem(String title, String description) {
        VBox box = new VBox(4);
        box.setStyle("-fx-background-color: #0d111d; -fx-border-color: #1e293b; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 14;");

        Label t = new Label(title);
        t.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #eaf0f7;");

        Label d = new Label(description);
        d.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
        d.setWrapText(true);

        box.getChildren().addAll(t, d);
        return box;
    }
}
