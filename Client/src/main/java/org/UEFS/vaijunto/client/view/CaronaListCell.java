package org.UEFS.vaijunto.client.view; // ajuste o pacote conforme a organização do seu projeto

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import org.UEFS.shared.dto.responses.CaronaResponse;
import org.UEFS.shared.dto.TrechoOcupacaoDTO; // ajuste o import conforme o pacote real
import org.UEFS.shared.dto.OtherUserDTO;       // ajuste o import conforme o pacote real

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Célula customizada para exibir uma {@link CaronaResponse} de forma visual:
 * - Data/hora da carona
 * - Selo de status colorido
 * - Barra horizontal segmentada por trecho, colorida conforme a ocupação
 *   (verde = livre, vermelho = lotado), com tooltip listando os passageiros
 * - Contagem total de passageiros únicos vs. vagas totais
 *
 * Uso:
 *   listaCaronasInscritas.setCellFactory(lv -> new CaronaListCell());
 *   listaCaronasCriadas.setCellFactory(lv -> new CaronaListCell());
 */
public class CaronaListCell extends ListCell<CaronaResponse> {

    private static final DateTimeFormatter DATA_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final VBox root = new VBox(8);
    private final Label lblData = new Label();
    private final Label lblStatus = new Label();
    private final Label lblVagas = new Label();
    private final HBox trechoBar = new HBox();

    // Espaço reservado para a barra de rolagem da ListView + margem visual do card
    private static final double MARGEM_LARGURA = 24.0;

    public CaronaListCell() {
        super();
        setStyle("-fx-background-color: transparent;"); // evita fundo duplicado (célula + card)
        montarLayout();

        // IMPORTANTE: por padrão, o maxWidth de qualquer Region (inclusive um ListCell)
        // é "infinito" — nada impede a célula de crescer. Combinado ao VirtualFlow
        // interno da ListView recalculando o layout a cada seleção/clique, isso gera
        // um loop de realimentação (a célula cresce, o próximo layout usa esse valor
        // maior como base, cresce de novo...) até "estourar" a tela.
        //
        // A correção: travar a largura da PRÓPRIA CÉLULA (não só do card interno) à
        // largura da ListView, e capar o máximo exatamente nesse valor.
        listViewProperty().addListener((obs, ligaAnterior, novaListView) -> {
            if (novaListView != null) {
                prefWidthProperty().bind(novaListView.widthProperty().subtract(MARGEM_LARGURA));
                setMaxWidth(Region.USE_PREF_SIZE); // trava o máximo no valor do prefWidth acima
            }
        });

        // O card interno só precisa acompanhar a largura JÁ TRAVADA da célula —
        // não precisa (e não deve) olhar de novo para a ListView.
        root.setMaxWidth(Double.MAX_VALUE);
        root.prefWidthProperty().bind(widthProperty());

        // Duplo clique abre o popup com os detalhes — usa getItem() (o dado desta
        // célula específica) em vez do modelo de seleção da ListView, para nunca
        // correr o risco de abrir os detalhes de uma carona diferente da clicada.
        setOnMouseClicked(evento -> {
            if (evento.getClickCount() == 2 && evento.getButton() == MouseButton.PRIMARY) {
                CaronaResponse carona = getItem();
                if (carona != null) {
                    CaronaDetalhesDialog.mostrar(carona);
                }
            }
        });
    }

    private void montarLayout() {
        lblData.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #212529;");
        lblVagas.setStyle("-fx-font-size: 12px; -fx-text-fill: #555;");

        HBox topo = new HBox(10, lblData, lblStatus);
        topo.setAlignment(Pos.CENTER_LEFT);
        topo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(lblData, Priority.ALWAYS);

        trechoBar.setPrefHeight(14);
        trechoBar.setMinHeight(14);
        trechoBar.setSpacing(2);
        trechoBar.setMaxWidth(Double.MAX_VALUE);
        trechoBar.setStyle("-fx-background-color: #eee; -fx-background-radius: 4;");

        root.getChildren().addAll(topo, trechoBar, lblVagas);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: white; -fx-background-radius: 8; " +
                "-fx-border-color: #dee2e6; -fx-border-radius: 8;");
    }

    @Override
    protected void updateItem(CaronaResponse carona, boolean empty) {
        super.updateItem(carona, empty);

        if (empty || carona == null) {
            setText(null);
            setGraphic(null);
            return;
        }

        lblData.setText(carona.data().format(DATA_FORMATTER));
        aplicarStatus(carona.status());
        montarTrechoBar(carona);

        int totalPassageirosUnicos = carona.ocupacaoPorTrecho().stream()
                .flatMap(t -> t.passageiros().stream())
                .map(OtherUserDTO::id)
                .collect(Collectors.toSet())
                .size();

        lblVagas.setText(totalPassageirosUnicos + " passageiro(s) únicos • " +
                carona.vagasTotais() + " vaga(s) totais");

        setGraphic(root);
        setText(null);
    }

    private void aplicarStatus(String status) {
        lblStatus.setText(status);
        String cor = switch (status.toUpperCase()) {
            case "CONFIRMADA", "EM_ANDAMENTO" -> "#2ecc71";
            case "CANCELADA" -> "#e74c3c";
            case "FINALIZADA" -> "#95a5a6";
            default -> "#f39c12"; // pendente ou outro status
        };
        lblStatus.setStyle("-fx-background-color: " + cor + "; -fx-text-fill: white; " +
                "-fx-padding: 2 8 2 8; -fx-background-radius: 10; -fx-font-size: 11px;");
    }

    /**
     * Monta uma barra horizontal onde cada trecho vira um segmento colorido
     * proporcional à sua extensão, com a cor indicando o nível de ocupação
     * daquele trecho (passageiros / vagasTotais).
     */
    private void montarTrechoBar(CaronaResponse carona) {
        trechoBar.getChildren().clear();

        List<TrechoOcupacaoDTO> trechos = carona.ocupacaoPorTrecho().stream()
                .sorted(Comparator.comparingInt(t -> t.trecho().inicio()))
                .toList();

        if (trechos.isEmpty()) return;

        int inicioRota = trechos.get(0).trecho().inicio();
        int fimRota = trechos.stream().mapToInt(t -> t.trecho().fim()).max().orElse(inicioRota + 1);
        int extensaoRota = Math.max(1, fimRota - inicioRota);

        for (TrechoOcupacaoDTO trecho : trechos) {
            int extensaoTrecho = Math.max(1, trecho.trecho().fim() - trecho.trecho().inicio());
            double proporcao = (double) extensaoTrecho / extensaoRota;

            int ocupacao = trecho.passageiros().size();
            double percentualOcupacao = carona.vagasTotais() == 0
                    ? 0
                    : Math.min(1.0, (double) ocupacao / carona.vagasTotais());

            Region segmento = new Region();
            segmento.setPrefHeight(14);
            segmento.setStyle(String.format(
                    "-fx-background-color: %s; -fx-background-radius: 3;",
                    corOcupacao(percentualOcupacao)
            ));

            // Largura proporcional à extensão do trecho dentro da barra total
            segmento.prefWidthProperty().bind(trechoBar.widthProperty().multiply(proporcao));
            segmento.setMaxWidth(Region.USE_PREF_SIZE);

            String nomesPassageiros = trecho.passageiros().stream()
                    .map(p -> (p.nome() == null || p.nome().isBlank()) ? p.email() : p.nome())
                    .collect(Collectors.joining(", "));

            Tooltip tooltip = new Tooltip(String.format(
                    "Trecho %d–%d: %d/%d vaga(s)%s",
                    trecho.trecho().inicio(), trecho.trecho().fim(),
                    ocupacao, carona.vagasTotais(),
                    nomesPassageiros.isEmpty() ? "" : "\n" + nomesPassageiros
            ));
            tooltip.setShowDelay(javafx.util.Duration.millis(150));
            Tooltip.install(segmento, tooltip);

            trechoBar.getChildren().add(segmento);
        }
    }

    /** Interpola de verde (livre) para vermelho (lotado) conforme a ocupação. */
    private String corOcupacao(double percentual) {
        Color inicio = Color.web("#2ecc71");
        Color fim = Color.web("#e74c3c");
        Color cor = inicio.interpolate(fim, percentual);
        return String.format("#%02X%02X%02X",
                (int) (cor.getRed() * 255),
                (int) (cor.getGreen() * 255),
                (int) (cor.getBlue() * 255));
    }
}