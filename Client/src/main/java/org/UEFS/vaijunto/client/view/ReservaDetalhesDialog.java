package org.UEFS.vaijunto.client.view;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;

import org.UEFS.shared.dto.responses.ReservaResponse;
import org.UEFS.shared.dto.PassoItinerarioDTO; // ajuste o import conforme o pacote real

import java.util.List;
import java.util.function.IntFunction;

/**
 * Popup com todos os detalhes de uma reserva: status, tipo de itinerário
 * (direto ou com baldeação) e a lista completa de trechos, cada um com o
 * motorista e a carona responsável por aquele trecho.
 *
 * Uso:
 *   ReservaDetalhesDialog.mostrar(reservaSelecionada);
 *   // ou, se você tiver como traduzir id de cidade -> nome:
 *   ReservaDetalhesDialog.mostrar(reservaSelecionada, idCidade -> resolverNome(idCidade));
 */
public final class ReservaDetalhesDialog {

    private ReservaDetalhesDialog() {  }

    public static void mostrar(ReservaResponse reserva) {
        mostrar(reserva, null);
    }

    public static void mostrar(ReservaResponse reserva, IntFunction<String> nomeCidade) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Detalhes da Reserva");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(440);
        dialog.getDialogPane().setContent(montarConteudo(reserva, nomeCidade));
        dialog.showAndWait();
    }

    private static VBox montarConteudo(ReservaResponse reserva, IntFunction<String> nomeCidade) {
        VBox raiz = new VBox(12);
        raiz.setPadding(new Insets(10));

        Label lblId = new Label("Reserva #" + reserva.idReserva());
        lblId.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #212529;");

        Label lblStatus = new Label("Status: " + reserva.status().name());
        lblStatus.setStyle("-fx-font-size: 13px; -fx-text-fill: #212529;");

        List<PassoItinerarioDTO> passos = reserva.itinerario() != null && reserva.itinerario().passos() != null
                ? reserva.itinerario().passos()
                : List.of();

        long qtdMotoristas = passos.stream().map(PassoItinerarioDTO::idMotorista).distinct().count();
        Label lblTipo = new Label(qtdMotoristas <= 1
                ? "Carona direta"
                : "Com baldeação — " + qtdMotoristas + " motorista(s) diferentes");
        lblTipo.setStyle("-fx-font-size: 13px; -fx-text-fill: #212529;");

        Label lblTrechosTitulo = new Label("Trechos do itinerário");
        lblTrechosTitulo.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #212529;");

        VBox listaTrechos = new VBox(10);
        if (passos.isEmpty()) {
            listaTrechos.getChildren().add(rotuloCinza("Nenhum trecho cadastrado."));
        } else {
            int numero = 1;
            for (PassoItinerarioDTO passo : passos) {
                listaTrechos.getChildren().add(montarLinhaPasso(numero++, passo, nomeCidade));
            }
        }

        ScrollPane scroll = new ScrollPane(listaTrechos);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(220);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        raiz.getChildren().addAll(lblId, lblStatus, lblTipo, new Separator(), lblTrechosTitulo, scroll);
        return raiz;
    }

    private static VBox montarLinhaPasso(int numero, PassoItinerarioDTO passo, IntFunction<String> nomeCidade) {
        VBox caixa = new VBox(4);
        caixa.setPadding(new Insets(8));
        caixa.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 6; " +
                "-fx-border-color: #dee2e6; -fx-border-radius: 6;");

        String origem = nomeOuId(passo.inicio(), nomeCidade);
        String destino = nomeOuId(passo.fim(), nomeCidade);

        Label lblTitulo = new Label(numero + ". " + origem + " → " + destino);
        lblTitulo.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #212529;");

        Label lblMotorista = new Label("Motorista: " + passo.idMotorista());
        lblMotorista.setStyle("-fx-font-size: 12px; -fx-text-fill: #212529;");

        Label lblCarona = new Label("Carona: " + passo.idCarona());
        lblCarona.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");

        caixa.getChildren().addAll(lblTitulo, lblMotorista, lblCarona);
        return caixa;
    }

    private static String nomeOuId(int idCidade, IntFunction<String> nomeCidade) {
        if (nomeCidade != null) {
            String nome = nomeCidade.apply(idCidade);
            if (nome != null && !nome.isBlank()) return nome;
        }
        return "Cidade #" + idCidade;
    }

    private static Label rotuloCinza(String texto) {
        Label lbl = new Label(texto);
        lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        return lbl;
    }
}