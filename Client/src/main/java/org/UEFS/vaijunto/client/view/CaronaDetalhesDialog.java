package org.UEFS.vaijunto.client.view; // ajuste o pacote conforme a organização do seu projeto

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;

import org.UEFS.shared.dto.responses.CaronaResponse;
import org.UEFS.shared.dto.TrechoOcupacaoDTO; // ajuste o import conforme o pacote real
import org.UEFS.shared.dto.OtherUserDTO;       // ajuste o import conforme o pacote real

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Popup com todos os detalhes de uma carona específica: dados gerais,
 * status, e a lista completa de trechos com os respectivos passageiros.
 *
 * Uso:
 *   CaronaDetalhesDialog.mostrar(caronaSelecionada);
 */
public final class CaronaDetalhesDialog {

    private static final DateTimeFormatter DATA_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private CaronaDetalhesDialog() {}

    public static void mostrar(CaronaResponse carona) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Detalhes da Carona");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(440);
        dialog.getDialogPane().setContent(montarConteudo(carona));
        dialog.showAndWait();
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
        caixa.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 6; " +
                "-fx-border-color: #dee2e6; -fx-border-radius: 6;");

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