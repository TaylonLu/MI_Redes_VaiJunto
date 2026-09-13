package org.UEFS.client.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

public class MapaCaronaController {

    public enum ModoTela { CRIACAO, EXIBICAO, BUSCA }
    private ModoTela modoAtual = ModoTela.CRIACAO;

    // --- Painéis ---
    @FXML private VBox painelCriacao;
    @FXML private VBox painelExibicao;
    @FXML private VBox painelBusca;
    @FXML private VBox painelPopup;

    // --- Mapa ---
    @FXML private Pane containerMapa;

    // --- Componentes Criação ---
    @FXML private ListView<String> listViewPercursoCriacao;
    @FXML private Label lblStatusCriacao;

    // --- Componentes Exibição ---
    @FXML private ListView<String> listViewPercursoExibicao;
    @FXML private Label lblExibeMotorista, lblExibeDataHora, lblExibeVagas, lblExibeObs;

    // --- Componentes Busca ---
    @FXML private Label lblBuscaOrigem;
    @FXML private Label lblBuscaDestino;
    @FXML private ListView<String> listViewResultadosBusca;

    // Variáveis de controle de busca
    private String cidadeOrigemBusca = null;
    private String cidadeDestinoBusca = null;

    @FXML
    public void initialize() {
        // Adiciona um listener para quando o usuário clica em um resultado na busca
        listViewResultadosBusca.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                desenharRotaMulticoloridaNoMapa(newVal);
            }
        });
    }

    /**
     * Chamado pela tela anterior (Tela Inicial) para configurar o comportamento do Mapa
     */
    public void configurarModo(ModoTela modo, Object dadosAuxiliares) {
        this.modoAtual = modo;

        painelCriacao.setVisible(modo == ModoTela.CRIACAO);
        painelExibicao.setVisible(modo == ModoTela.EXIBICAO);
        painelBusca.setVisible(modo == ModoTela.BUSCA);

        limparMapa();

        if (modo == ModoTela.EXIBICAO && dadosAuxiliares != null) {
            carregarDadosCaronaExibicao(dadosAuxiliares);
        } else if (modo == ModoTela.BUSCA) {
            limparBusca(null);
        }
    }

    // ==========================================
    // INTERAÇÃO COM O MAPA
    // ==========================================
    @FXML
    void onMapaClicked(MouseEvent event) {
        String cidadeClicada = detectarCidadeClicada(event.getX(), event.getY());
        if (cidadeClicada == null) return;

        switch (modoAtual) {
            case CRIACAO:
                // Lógica que você já possui de adicionar cidade na lista de criação
                listViewPercursoCriacao.getItems().add(cidadeClicada);
                desenharLinhaNoMapa();
                break;

            case BUSCA:
                // Seleciona origem e depois destino
                if (cidadeOrigemBusca == null) {
                    cidadeOrigemBusca = cidadeClicada;
                    lblBuscaOrigem.setText("Origem: " + cidadeOrigemBusca);
                } else if (cidadeDestinoBusca == null && !cidadeClicada.equals(cidadeOrigemBusca)) {
                    cidadeDestinoBusca = cidadeClicada;
                    lblBuscaDestino.setText("Destino: " + cidadeDestinoBusca);
                }
                break;

            case EXIBICAO:
                // No modo exibição, clicar no mapa não faz nada ou mostra info da cidade
                break;
        }
    }

    // ==========================================
    // LÓGICA DE BUSCA
    // ==========================================
    @FXML
    void buscarCaronas(ActionEvent event) {
        if (cidadeOrigemBusca == null || cidadeDestinoBusca == null) {
            System.out.println("Selecione origem e destino antes de buscar!");
            return;
        }

        System.out.println("Enviando requisição ao servidor: " + cidadeOrigemBusca + " -> " + cidadeDestinoBusca);
        // Exemplo: simula resposta do servidor
        listViewResultadosBusca.getItems().clear();
        listViewResultadosBusca.getItems().add("Carona Direta - Motorista A");
        listViewResultadosBusca.getItems().add("Baldeação - Motoristas B e C");
    }

    @FXML
    void limparBusca(ActionEvent event) {
        cidadeOrigemBusca = null;
        cidadeDestinoBusca = null;
        lblBuscaOrigem.setText("Origem: (Não selecionada)");
        lblBuscaDestino.setText("Destino: (Não selecionado)");
        listViewResultadosBusca.getItems().clear();
        limparMapa();
    }

    private void desenharRotaMulticoloridaNoMapa(String caronaSelecionada) {
        limparMapa();
        // Aqui você acessaria o objeto real da carona vindo do servidor
        // Se a carona for dividida entre 2 motoristas, você itera pelas rotas.

        System.out.println("Desenhando rotas para: " + caronaSelecionada);

        // Exemplo teórico:
        // if (carona.isBaldeacao()) {
        //     desenharLinha(rotaMotorista1, javafx.scene.paint.Color.BLUE);
        //     desenharLinha(rotaMotorista2, javafx.scene.paint.Color.GREEN);
        // } else {
        //     desenharLinha(rotaMotorista, javafx.scene.paint.Color.BLUE);
        // }
    }

    // ==========================================
    // LÓGICA DE EXIBIÇÃO
    // ==========================================
    private void carregarDadosCaronaExibicao(Object dadosCarona) {
        // Povoa os labels e o listViewPercursoExibicao com os dados recebidos
        lblExibeMotorista.setText("Motorista: João da Silva");
        lblExibeDataHora.setText("Partida: 20/09/2026 às 14:30");
        lblExibeVagas.setText("Vagas disponíveis: 2");
        lblExibeObs.setText("Obs: Aceito Pets. Ar condicionado ligado.");

        listViewPercursoExibicao.getItems().addAll("Feira de Santana", "Amélia Rodrigues", "Salvador");

        // Pinta a rota no mapa
        desenharLinhaNoMapa();
    }

    // ==========================================
    // LÓGICA DE CRIAÇÃO (Já existente)
    // ==========================================
    @FXML void confirmarRota(ActionEvent event) { painelPopup.setVisible(true); }
    @FXML void fecharPopup(ActionEvent event) { painelPopup.setVisible(false); }
    @FXML void recomecarRota(ActionEvent event) { listViewPercursoCriacao.getItems().clear(); limparMapa(); }
    @FXML void enviarCaronaServidor(ActionEvent event) { /* Lógica servidor */ }

    // ==========================================
    // UTILITÁRIOS
    // ==========================================
    @FXML
    void voltar(ActionEvent event) {
        System.out.println("Voltando para a tela inicial...");
        // Lógica para voltar de cena
    }

    @FXML void onMapaDragged(MouseEvent event) {}
    @FXML void onMapaPressed(MouseEvent event) {}

    private void limparMapa() {
        // Remove linhas desenhadas anteriormente do grupo mapaGrupo
    }

    private void desenharLinhaNoMapa() {
        // Lógica existente de desenhar linha
    }

    private String detectarCidadeClicada(double x, double y) {
        // Lógica de colisão/clique com os nós do seu mapa
        return "Cidade Exemplo"; // Retorno mockado
    }
}