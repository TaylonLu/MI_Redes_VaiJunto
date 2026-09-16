package org.UEFS.vaijunto.client.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.UEFS.shared.dto.responses.ReservaResponse;
import org.UEFS.shared.dto.PassoItinerarioDTO; // ajuste o import conforme o pacote real
import org.UEFS.vaijunto.client.view.ReservaDetalhesDialog; // ajuste o pacote conforme seu projeto

import java.util.List;
import java.util.function.IntFunction;

/**
 * Célula customizada para exibir uma {@link ReservaResponse}: status, se é
 * carona direta ou com baldeação, e os trechos do itinerário como "chips"
 * coloridos por motorista (a cor muda sempre que o motorista muda ao longo
 * do trajeto).
 *
 * Uso:
 *   listaCaronasInscritas.setCellFactory(lv -> new ReservaListCell());
 *   // ou, se você tiver como traduzir id de cidade -> nome:
 *   listaCaronasInscritas.setCellFactory(lv -> new ReservaListCell(idCidade -> resolverNome(idCidade)));
 */
public class ReservaListCell extends ListCell<ReservaResponse> {

    private static final String[] CORES_MOTORISTA = {"#27ae60", "#e74c3c", "#9b59b6", "#f39c12", "#3498db"};
    private static final double MARGEM_LARGURA = 24.0;

    private final IntFunction<String> nomeCidade;

    private final VBox root = new VBox(8);
    private final Label lblTitulo = new Label();
    private final Label lblStatus = new Label();
    private final Label lblResumo = new Label();
    private final FlowPane trechosBox = new FlowPane(6, 6);

    public ReservaListCell() {
        this(null);
    }

    /**
     * @param nomeCidade função opcional que traduz um id de cidade para o nome
     *                   exibível. Se null (ou retornar vazio para um id), mostra
     *                   apenas "#id".
     */
    public ReservaListCell(IntFunction<String> nomeCidade) {
        super();
        this.nomeCidade = nomeCidade;
        setStyle("-fx-background-color: transparent;"); // evita fundo duplicado (célula + card)
        montarLayout();

        // Mesma correção de largura aplicada ao CaronaListCell: trava o máximo
        // da PRÓPRIA CÉLULA na largura da ListView, evitando o loop de
        // crescimento do VirtualFlow.
        listViewProperty().addListener((obs, ligaAnterior, novaListView) -> {
            if (novaListView != null) {
                prefWidthProperty().bind(novaListView.widthProperty().subtract(MARGEM_LARGURA));
                setMaxWidth(Region.USE_PREF_SIZE);
            }
        });

        root.setMaxWidth(Double.MAX_VALUE);
        root.prefWidthProperty().bind(widthProperty());

        // Duplo clique abre o popup de detalhes, usando getItem() (o dado desta
        // célula específica) para nunca correr risco de abrir a reserva errada.
        setOnMouseClicked(evento -> {
            if (evento.getClickCount() == 2 && evento.getButton() == MouseButton.PRIMARY) {
                ReservaResponse reserva = getItem();
                if (reserva != null) {
                    ReservaDetalhesDialog.mostrar(reserva, nomeCidade);
                }
            }
        });
    }

    private void montarLayout() {
        lblTitulo.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #212529;");
        lblResumo.setStyle("-fx-font-size: 12px; -fx-text-fill: #555;");

        HBox topo = new HBox(10, lblTitulo, lblStatus);
        topo.setAlignment(Pos.CENTER_LEFT);
        topo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(lblTitulo, Priority.ALWAYS);

        trechosBox.setMaxWidth(Double.MAX_VALUE);

        root.getChildren().addAll(topo, trechosBox, lblResumo);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: white; -fx-background-radius: 8; " +
                "-fx-border-color: #dee2e6; -fx-border-radius: 8;");
    }

    @Override
    protected void updateItem(ReservaResponse reserva, boolean empty) {
        super.updateItem(reserva, empty);

        if (empty || reserva == null) {
            setText(null);
            setGraphic(null);
            return;
        }

        List<PassoItinerarioDTO> passos = obterPassos(reserva);
        long qtdMotoristas = passos.stream().map(PassoItinerarioDTO::idMotorista).distinct().count();

        lblTitulo.setText(qtdMotoristas <= 1 ? "Carona Direta" : "Baldeação (" + qtdMotoristas + " carros)");
        aplicarStatus(reserva.status().name());
        montarTrechos(passos);

        lblResumo.setText(passos.size() + " trecho(s) • Reserva #" + abreviarId(reserva.idReserva()));

        setGraphic(root);
        setText(null);
    }

    private List<PassoItinerarioDTO> obterPassos(ReservaResponse reserva) {
        if (reserva.itinerario() == null || reserva.itinerario().passos() == null) {
            return List.of();
        }
        return reserva.itinerario().passos();
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

    /** Monta um "chip" por trecho, colorido por motorista (muda de cor a cada troca de motorista). */
    private void montarTrechos(List<PassoItinerarioDTO> passos) {
        trechosBox.getChildren().clear();
        if (passos.isEmpty()) return;

        String motoristaAtual = passos.get(0).idMotorista();
        int corIndex = 0;

        for (PassoItinerarioDTO passo : passos) {
            if (!passo.idMotorista().equals(motoristaAtual)) {
                motoristaAtual = passo.idMotorista();
                corIndex = (corIndex + 1) % CORES_MOTORISTA.length;
            }

            Label chip = new Label(nomeOuId(passo.inicio()) + " → " + nomeOuId(passo.fim()));
            chip.setStyle(String.format(
                    "-fx-background-color: %s; -fx-text-fill: white; -fx-font-size: 11px; " +
                            "-fx-padding: 3 8 3 8; -fx-background-radius: 6;",
                    CORES_MOTORISTA[corIndex]
            ));

            Tooltip tooltip = new Tooltip("Motorista: " + passo.idMotorista() + "\nCarona: " + passo.idCarona());
            Tooltip.install(chip, tooltip);

            trechosBox.getChildren().add(chip);
        }
    }

    private String nomeOuId(int idCidade) {
        if (nomeCidade != null) {
            String nome = nomeCidade.apply(idCidade);
            if (nome != null && !nome.isBlank()) return nome;
        }
        return "#" + idCidade;
    }

    private String abreviarId(String id) {
        if (id == null) return "-";
        return id.length() <= 8 ? id : id.substring(0, 8);
    }
}