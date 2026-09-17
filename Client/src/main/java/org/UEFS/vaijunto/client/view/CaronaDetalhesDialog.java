package org.UEFS.vaijunto.client.view;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.UEFS.shared.dto.ItinerarioDTO;
import org.UEFS.shared.dto.OtherUserDTO;
import org.UEFS.shared.dto.PassoItinerarioDTO;
import org.UEFS.shared.dto.TrechoOcupacaoDTO;
import org.UEFS.shared.dto.responses.CaronaResponse;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class CaronaDetalhesDialog {

    private static final DateTimeFormatter DATA_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private CaronaDetalhesDialog() {}

    /**
     * Exibe o dialog de detalhes para um Itinerário vindo da busca.
     * Retorna true se o usuário clicar no botão "Confirmar Participação".
     */
    public static boolean mostrar(ItinerarioDTO itinerario) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Detalhes do Itinerário");
        dialog.setHeaderText(null);

        ButtonType btnConfirmar = new ButtonType("Confirmar Participação", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnConfirmar, ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(440);
        dialog.getDialogPane().setContent(montarConteudoItinerario(itinerario));

        Optional<ButtonType> resultado = dialog.showAndWait();
        return resultado.isPresent() && resultado.get() == btnConfirmar;
    }

    /**
     * Exibe o dialog para uma CaronaResponse individual.
     */
    public static boolean mostrar(CaronaResponse carona, boolean comConfirmacao) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Detalhes da Carona");
        dialog.setHeaderText(null);

        ButtonType btnConfirmar = new ButtonType("Confirmar Participação", ButtonBar.ButtonData.OK_DONE);
        if (comConfirmacao) {
            dialog.getDialogPane().getButtonTypes().addAll(btnConfirmar, ButtonType.CLOSE);
        } else {
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        }

        dialog.getDialogPane().setPrefWidth(440);
        dialog.getDialogPane().setContent(montarConteudo(carona));

        Optional<ButtonType> resultado = dialog.showAndWait();
        return resultado.isPresent() && resultado.get() == btnConfirmar;
    }

    public static void mostrar(CaronaResponse carona) {
        mostrar(carona, false);
    }

    private static VBox montarConteudoItinerario(ItinerarioDTO itinerario) {
        VBox raiz = new VBox(12);
        raiz.setPadding(new Insets(10));

        Label lblTitulo = new Label("Resumo do Itinerário");
        lblTitulo.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #212529;");

        long qtdMotoristas = itinerario.passos().stream().map(PassoItinerarioDTO::idMotorista).distinct().count();
        String tipoViagem = qtdMotoristas <= 1 ? "Carona Direta" : "Viagem com Baldeação (" + qtdMotoristas + " veículos)";

        Label lblTipo = new Label("Tipo: " + tipoViagem);
        lblTipo.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #27ae60;");

        Label lblTrechosTitulo = new Label("Trechos do Percurso");
        lblTrechosTitulo.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #212529;");

        VBox listaTrechos = new VBox(10);
        for (int i = 0; i < itinerario.passos().size(); i++) {
            PassoItinerarioDTO passo = itinerario.passos().get(i);
            VBox caixa = new VBox(4);
            caixa.setPadding(new Insets(8));
            caixa.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 6; -fx-border-color: #dee2e6; -fx-border-radius: 6;");

            Label lblPasso = new Label(String.format("Passo %d: Cidade %d ➔ Cidade %d", i + 1, passo.inicio(), passo.fim()));
            lblPasso.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #212529;");

            Label lblMotorista = new Label("Motorista ID: " + passo.idMotorista());
            lblMotorista.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");

            Label lblCarona = new Label("Carona ID: " + passo.idCarona());
            lblCarona.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");

            caixa.getChildren().addAll(lblPasso, lblMotorista, lblCarona);
            listaTrechos.getChildren().add(caixa);
        }

        ScrollPane scroll = new ScrollPane(listaTrechos);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(220);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        raiz.getChildren().addAll(lblTitulo, lblTipo, new Separator(), lblTrechosTitulo, scroll);
        return raiz;
    }

    private static VBox montarConteudo(CaronaResponse carona) {
        VBox raiz = new VBox(12);
        raiz.setPadding(new Insets(10));

        Label lblData = new Label(carona.data().format(DATA_FORMATTER));
        lblData.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #212529;");

        Label lblStatus = new Label("Status: " + carona.status());
        lblStatus.setStyle("-fx-font-size: 13px; -fx-text-fill: #212529;");

        Label lblVagas = new Label("Vagas totais: " + carona.vagasTotais());
        lblVagas.setStyle("-fx-font-size: 13px; -fx-text-fill: #212529;");

        Label lblId = new Label("ID da carona: " + carona.id());
        lblId.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");

        Label lblMotorista = new Label("ID do motorista: " + carona.idMotorista());
        lblMotorista.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");

        Label lblTrechosTitulo = new Label("Trechos e passageiros");
        lblTrechosTitulo.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #212529;");

        VBox listaTrechos = new VBox(10);
        List<TrechoOcupacaoDTO> trechos = carona.ocupacaoPorTrecho().stream()
                .sorted(Comparator.comparingInt(t -> t.trecho().inicio()))
                .toList();

        if (trechos.isEmpty()) {
            listaTrechos.getChildren().add(new Label("Nenhum trecho cadastrado."));
        } else {
            for (TrechoOcupacaoDTO trecho : trechos) {
                listaTrechos.getChildren().add(montarLinhaTrecho(trecho, carona.vagasTotais()));
            }
        }

        ScrollPane scroll = new ScrollPane(listaTrechos);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(220);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        raiz.getChildren().addAll(
                lblData, lblStatus, lblVagas, lblId, lblMotorista,
                new Separator(), lblTrechosTitulo, scroll
        );
        return raiz;
    }

    private static VBox montarLinhaTrecho(TrechoOcupacaoDTO trecho, int vagasTotais) {
        VBox caixa = new VBox(4);
        caixa.setPadding(new Insets(8));
        caixa.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 6; -fx-border-color: #dee2e6; -fx-border-radius: 6;");

        Label lblTrecho = new Label(String.format("Trecho %d – %d (%d/%d vagas)",
                trecho.trecho().inicio(), trecho.trecho().fim(),
                trecho.passageiros().size(), vagasTotais));
        lblTrecho.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #212529;");
        caixa.getChildren().add(lblTrecho);

        if (trecho.passageiros().isEmpty()) {
            Label lblVazio = new Label("Nenhum passageiro neste trecho.");
            lblVazio.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
            caixa.getChildren().add(lblVazio);
        } else {
            for (OtherUserDTO passageiro : trecho.passageiros()) {
                String nomeExibido = (passageiro.nome() == null || passageiro.nome().isBlank())
                        ? passageiro.email()
                        : passageiro.nome();
                Label lblPassageiro = new Label("• " + nomeExibido + " (" + passageiro.email() + ")");
                lblPassageiro.setStyle("-fx-font-size: 13px; -fx-text-fill: #3d0202");
                caixa.getChildren().add(lblPassageiro);
            }
        }

        return caixa;
    }
}