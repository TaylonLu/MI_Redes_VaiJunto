package org.UEFS.vaijunto.client.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import org.UEFS.shared.FileManager;
import org.UEFS.shared.JsonUtils;
import org.UEFS.shared.dto.*;
import org.UEFS.shared.dto.requests.BuscaCaronaRequest;
import org.UEFS.shared.dto.requests.NovaCaronaRequest;
import org.UEFS.shared.dto.responses.GeneralResponse;
import org.UEFS.shared.model.Cidade;
import org.UEFS.vaijunto.client.service.ClientRouter;
import org.UEFS.vaijunto.client.service.NetworkDispatcher;
import org.UEFS.vaijunto.client.service.SessionManager;
import org.UEFS.vaijunto.client.utils.Resources;
import org.UEFS.vaijunto.client.utils.Toast;
import org.UEFS.vaijunto.client.view.CaronaDetalhesDialog;
import org.uefs.custom.CurrencyField;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

public class MapaCaronaController {
    public enum ModoTela { CRIACAO, EXIBICAO, EXIBICAO_PASSAGEIRO, EXIBICAO_MOTORISTA, BUSCA }
    private ModoTela modoAtual = ModoTela.CRIACAO;

    // ==========================================
    // INJEÇÕES FXML - AGRUPADAS POR CONTEXTO
    // ==========================================

    // --- Globais e Mapa ---
    @FXML private ImageView imagemMapa;
    @FXML private Pane containerMapa;
    @FXML private StackPane stackContainer;
    @FXML private Group mapaGrupo;
    @FXML private Button btnRecomecar;
    @FXML private Button btnCancelar;
    @FXML private VBox painelPopup;
    @FXML private StackPane painelLoading;

    // --- Modo: Criação ---
    @FXML private VBox painelCriacao;
    @FXML private ListView<String> listViewPercursoCriacao;
    @FXML private Label lblStatusCriacao;
    @FXML private DatePicker datePickerPartida;
    @FXML private Spinner<Integer> spinnerHora;
    @FXML private Spinner<Integer> spinnerMinuto;
    @FXML private TextField txtVagas;
    @FXML private CurrencyField valorTrecho;
    @FXML private TextField txtComentarios;

    // --- Modo: Busca ---
    @FXML private VBox painelBusca;
    @FXML private Label lblBuscaOrigem;
    @FXML private Label lblBuscaDestino;
    @FXML private TextField txtBuscaVagas;
    @FXML private DatePicker datePickerBusca;
    @FXML private ListView<ItinerarioDTO> listViewResultadosBusca;
    @FXML private Button btnVerDetalhes;

    // --- Modo: Exibição ---
    @FXML private VBox painelExibicao;
    @FXML private ListView<String> listViewPercursoExibicao;
    @FXML private Label lblExibeMotorista, lblExibeDataHora, lblExibeVagas, lblExibeObs;


    // ==========================================
    // VARIÁVEIS DE ESTADO E FORMATAÇÃO
    // ==========================================
    private Cidade cidadeOrigemBusca = null;
    private Cidade cidadeDestinoBusca = null;
    private Cidade cidadeAnteriorCriacao = null;
    private double anchorX;
    private double anchorY;
    private final List<Cidade> cidades = new ArrayList<>();
    private final List<Trecho> rotaMotorista = new ArrayList<>();

    private static final UnaryOperator<TextFormatter.Change> onlyNumberFormatter = change -> {
        if (change.getText().matches("\\d*")) return change;
        return null;
    };


    // ==========================================
    // 1. INICIALIZAÇÃO E CONFIGURAÇÃO DA TELA
    // ==========================================
    @FXML
    public void initialize() {
        // Setup visual do Mapa
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(containerMapa.widthProperty());
        clip.heightProperty().bind(containerMapa.heightProperty());
        containerMapa.setClip(clip);

        // Setup dos inputs (Filtro de números e Spinners)
        txtVagas.setTextFormatter(new TextFormatter<>(onlyNumberFormatter));
        txtBuscaVagas.setTextFormatter(new TextFormatter<>(onlyNumberFormatter));
        spinnerHora.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23));
        spinnerMinuto.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59));

        painelPopup.setVisible(false);

        // Carrega dados vitais
        carregarCidades();
        carregarImagemFundo();
        renderizarMapa();

        ClientRouter.inscrever("RESERVA_CONFIRMADA", this::receberRespostaReserva);
        ClientRouter.inscrever("CONFLITO_RESERVA", this::receberRespostaReserva);

        // Setup da renderização da lista de buscas
        listViewResultadosBusca.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(ItinerarioDTO item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.passos().isEmpty()) {
                    setText(null);
                } else {
                    long qtdMotoristas = item.passos().stream().map(PassoItinerarioDTO::idMotorista).distinct().count();
                    if (qtdMotoristas <= 1) {
                        setText("Carona Direta (" + item.passos().size() + " trechos)");
                    } else {
                        setText("Baldeação: " + qtdMotoristas + " carros (" + item.passos().size() + " trechos)");
                    }
                }
            }
        });

        listViewResultadosBusca.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                desenharRotaMulticoloridaNoMapa(newVal);
                btnVerDetalhes.setVisible(true);
                btnVerDetalhes.setManaged(true);
            } else {
                btnVerDetalhes.setVisible(false);
                btnVerDetalhes.setManaged(false);
            }
        });
    }

    public void configurarModo(ModoTela modo, Object dadosAuxiliares) {
        this.modoAtual = modo;

        painelCriacao.setVisible(modo == ModoTela.CRIACAO);
        painelExibicao.setVisible(modo == ModoTela.EXIBICAO || modo == ModoTela.EXIBICAO_PASSAGEIRO || modo == ModoTela.EXIBICAO_MOTORISTA);
        painelBusca.setVisible(modo == ModoTela.BUSCA);
        painelPopup.setVisible(false);

        limparMapaDinamico();

        if ((modo == ModoTela.EXIBICAO || modo == ModoTela.EXIBICAO_PASSAGEIRO || modo == ModoTela.EXIBICAO_MOTORISTA) && dadosAuxiliares != null) {
            carregarDadosCaronaExibicao(dadosAuxiliares);
        } else if (modo == ModoTela.BUSCA) {
            limparBusca(null);
        } else if (modo == ModoTela.CRIACAO) {
            recomecarRota(null);
        }
    }


    // ==========================================
    // 2. RENDERIZAÇÃO E INTERAÇÃO COM O MAPA
    // ==========================================
    private void carregarCidades() {
        try {
            this.cidades.clear();
            this.cidades.addAll(Objects.requireNonNull(FileManager.carregarDeJson("data/cidades.json")));
        } catch (RuntimeException e) {
            System.err.println("Erro ao carregar cidades.json: " + e.getMessage());
        }
    }

    private void carregarImagemFundo() {
        try {
            InputStream resource = Resources.getStream("/images/mapa_kalos.jpg");
            imagemMapa.setImage(new Image(resource));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void renderizarMapa() {
        for (Cidade C1 : cidades) {
            for (int idVizinho : C1.estradas) {
                Cidade C2 = cidades.stream().filter(C -> C.id == idVizinho).findFirst().orElse(null);
                if (C2 == null || C1.id > C2.id) continue;

                Line linha = new Line(C1.x, C1.y, C2.x, C2.y);
                linha.getStyleClass().add("estrada-linha");
                mapaGrupo.getChildren().add(linha);
            }

            Circle circulo = new Circle(C1.x, C1.y, 10);
            circulo.getStyleClass().add("cidade-no");
            circulo.setUserData(C1);

            Text texto = new Text(C1.x + 15, C1.y + 5, C1.nome);
            texto.setFont(Font.font("System", FontWeight.BOLD, 12));
            texto.getStyleClass().add("cidade-texto");
            texto.visibleProperty().bind(circulo.hoverProperty());

            circulo.setOnMouseClicked(_ -> handleCidadeClick(C1, circulo));

            mapaGrupo.getChildren().addAll(circulo, texto);
        }
    }

    private void handleCidadeClick(Cidade cidadeClicada, Circle circulo) {
        switch (modoAtual) {
            case CRIACAO:
                if (cidadeAnteriorCriacao == null) {
                    cidadeAnteriorCriacao = cidadeClicada;
                    circulo.getStyleClass().add("cidade-selecionada");
                    lblStatusCriacao.setText("Origem definida: " + cidadeClicada.nome);
                    listViewPercursoCriacao.getItems().add(cidadeClicada.nome);
                } else {
                    if (!saoAdjacentes(cidadeAnteriorCriacao, cidadeClicada)) {
                        Toast.error("Não há estrada direta entre " + cidadeAnteriorCriacao.nome + " e " + cidadeClicada.nome);
                        return;
                    }

                    Line linhaRota = new Line(cidadeAnteriorCriacao.x, cidadeAnteriorCriacao.y, cidadeClicada.x, cidadeClicada.y);
                    linhaRota.getStyleClass().add("rota-motorista-linha");
                    mapaGrupo.getChildren().addFirst(linhaRota);

                    rotaMotorista.add(new Trecho(cidadeAnteriorCriacao.id, cidadeClicada.id));
                    cidadeAnteriorCriacao = cidadeClicada;
                    circulo.getStyleClass().add("cidade-selecionada");
                    lblStatusCriacao.setText("Trecho adicionado até: " + cidadeClicada.nome);
                    listViewPercursoCriacao.getItems().add(cidadeClicada.nome);
                }
                break;


            case BUSCA:
                if (cidadeOrigemBusca == null) {
                    cidadeOrigemBusca = cidadeClicada;
                    circulo.getStyleClass().add("cidade-origem");
                    lblBuscaOrigem.setText("Origem: " + cidadeOrigemBusca.nome);
                } else if (cidadeDestinoBusca == null && !cidadeClicada.equals(cidadeOrigemBusca)) {
                    cidadeDestinoBusca = cidadeClicada;
                    circulo.getStyleClass().add("cidade-destino");
                    lblBuscaDestino.setText("Destino: " + cidadeDestinoBusca.nome);
                }
                break;

            default:
                break;
        }
    }

    /**
     * Verifica se duas cidades são conectadas por uma estrada, considerando a
     * ligação como via dupla: basta UM dos dois lados listar o outro na sua
     * lista de {@code estradas}.
     */
    private boolean saoAdjacentes(Cidade a, Cidade b) {
        return a.estradas.contains(b.id) || b.estradas.contains(a.id);
    }

    @FXML void onMapaClicked(MouseEvent event) { containerMapa.setCursor(Cursor.DEFAULT); }

    @FXML void onMapaPressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) return;

        anchorX = event.getSceneX() - mapaGrupo.getTranslateX();
        anchorY = event.getSceneY() - mapaGrupo.getTranslateY();
        containerMapa.setCursor(Cursor.CLOSED_HAND);
    }

    @FXML void onMapaDragged(MouseEvent mouseEvent) {
        if (mouseEvent.getButton() != MouseButton.PRIMARY) return;
        mapaGrupo.setTranslateX(mouseEvent.getSceneX() - anchorX);
        mapaGrupo.setTranslateY(mouseEvent.getSceneY() - anchorY);
    }

    // ==========================================
    // 3. MODO: CRIAÇÃO DE CARONAS
    // ==========================================
    @FXML
    void confirmarRota(ActionEvent event) {
        if (rotaMotorista.isEmpty()) {
            Toast.error("Você precisa selecionar pelo menos duas cidades conectadas!");
            return;
        }
        painelPopup.setVisible(true);
    }

    @FXML
    void enviarCaronaServidor(ActionEvent event) {
        if (txtVagas.getText().isEmpty() || datePickerPartida.getValue() == null) {
            Toast.warning("Por favor, informe a data de partida e o número de vagas.");
            return;
        }

        try {
            SelfUserDTO user = SessionManager.getInstance().getCurrentUser();
            LocalDateTime dataPartida = getDate();

            if (dataPartida.isBefore(LocalDateTime.now())) {
                Toast.error("A data e hora de partida não pode estar no passado.");
                return;
            }

            NovaCaronaRequest novaCarona = new NovaCaronaRequest(
                    user.id(), Integer.parseInt(txtVagas.getText()),
                    valorTrecho.getAmount(), dataPartida, rotaMotorista
            );

            String payload = JsonUtils.toJson(novaCarona);
            String comando = String.format("CRIAR_CARONA|%s|%s|%d", payload, SessionManager.getInstance().getUserToken(), payload.length());

            NetworkDispatcher.enviarComando(comando);

            Toast.success("Carona enviada com sucesso!");
            fecharPopup(null);
            recomecarRota(null);
            SceneManager.pop();

        } catch (Exception e) {
            Toast.error("Erro ao validar dados da carona. Verifique os campos.");
        }
    }

    @FXML void fecharPopup(ActionEvent event) { painelPopup.setVisible(false); }

    @FXML
    void recomecarRota(ActionEvent event) {
        listViewPercursoCriacao.getItems().clear();
        cidadeAnteriorCriacao = null;
        lblStatusCriacao.setText("Clique na origem da rota.");
        limparMapaDinamico();
    }

    private LocalDateTime getDate() {
        LocalDate date = datePickerPartida.getValue();
        return LocalDateTime.of(date.getYear(), date.getMonth(), date.getDayOfMonth(), spinnerHora.getValue(), spinnerMinuto.getValue());
    }


    // ==========================================
    // 4. MODO: BUSCA DE CARONAS
    // ==========================================
    @FXML
    void buscarCaronas(ActionEvent event) {
        if (cidadeOrigemBusca == null || cidadeDestinoBusca == null) {
            Toast.warning("Selecione origem e destino no mapa antes de buscar!");
            return;
        }
        if (txtBuscaVagas.getText().isEmpty() || datePickerBusca.getValue() == null) {
            Toast.warning("Informe a quantidade de vagas e a data pretendida para a busca.");
            return;
        }

        int vagasNecessarias = Integer.parseInt(txtBuscaVagas.getText());
        LocalDate dataPretendida = datePickerBusca.getValue();

        LocalDateTime dataHoraPretendida = dataPretendida.atStartOfDay();

        BuscaCaronaRequest request = new BuscaCaronaRequest(
                cidadeOrigemBusca.id, cidadeDestinoBusca.id, vagasNecessarias, dataHoraPretendida
        );
        String payload = JsonUtils.toJson(request);
        String comando = String.format("BUSCAR_ITINERARIOS|%s|%s|%d", payload, SessionManager.getInstance().getUserToken(), payload.length());

        ClientRouter.inscrever("ITINERARIOS_BUSCADOS", this::receberRespostaBusca);
        NetworkDispatcher.enviarComando(comando);

        listViewResultadosBusca.getItems().clear();
    }

    public void receberRespostaBusca(GeneralResponse resposta) {
        if (resposta.codigo() >= 200 && resposta.codigo() < 300) {
            List<ItinerarioDTO> resultados = JsonUtils.fromJsonList(resposta.dados(), ItinerarioDTO.class);

            Platform.runLater(() -> {
                listViewResultadosBusca.getItems().clear();
                if (resultados.isEmpty()) {
                    Toast.warning("Nenhuma carona encontrada para esse trajeto.");
                } else {
                    listViewResultadosBusca.getItems().addAll(resultados);
                    Toast.success(resultados.size() + " rotas encontradas!");
                }
            });
        } else {
            Platform.runLater(() -> Toast.error("Erro na busca: " + resposta.dados()));
        }
    }

    @FXML
    void abrirDetalhesEConfirmar(ActionEvent event) {
        ItinerarioDTO itinerarioSelecionado = listViewResultadosBusca.getSelectionModel().getSelectedItem();

        if (itinerarioSelecionado == null) {
            Toast.warning("Selecione uma rota na lista primeiro.");
            return;
        }

        boolean confirmou = CaronaDetalhesDialog.mostrar(itinerarioSelecionado);

        if (confirmou) confirmarReservaServidor(itinerarioSelecionado);
    }

    private void confirmarReservaServidor(ItinerarioDTO itinerarioSelecionado) {
        try {
            painelLoading.setVisible(true);
            painelLoading.setManaged(true);

            String payload = JsonUtils.toJson(itinerarioSelecionado);
            String comando = String.format("CONFIRMAR_RESERVA|%s|%s|%d",
                    payload,
                    SessionManager.getInstance().getUserToken(),
                    payload.length()
            );

            System.out.println("Comando de confirmação: \n" + comando);

            NetworkDispatcher.enviarComando(comando);

        } catch (Exception e) {
            painelLoading.setVisible(false);
            painelLoading.setManaged(false);
            Toast.error("Erro ao processar reserva da carona.");
            e.printStackTrace();
        }
    }

    @FXML
    void limparBusca(ActionEvent event) {
        cidadeOrigemBusca = null;
        cidadeDestinoBusca = null;
        lblBuscaOrigem.setText("Origem: (Não selecionada)");
        lblBuscaDestino.setText("Destino: (Não selecionado)");
        txtBuscaVagas.clear();
        datePickerBusca.setValue(null);
        listViewResultadosBusca.getItems().clear();
        limparMapaDinamico();
    }

    private void desenharRotaMulticoloridaNoMapa(ItinerarioDTO itinerario) {
        limparMapaDinamico();
        if (itinerario == null || itinerario.passos() == null || itinerario.passos().isEmpty()) return;

        reaplicarDestaquesBusca();

        String[] cores = {"#27ae60", "#e74c3c", "#9b59b6", "#f39c12", "#3498db"};
        int corIndex = 0;

        String motoristaAtual = itinerario.passos().getFirst().idMotorista();

        int indexInsercao = 0;
        for (int i = 0; i < mapaGrupo.getChildren().size(); i++) {
            if (mapaGrupo.getChildren().get(i) instanceof Circle) {
                indexInsercao = i;
                break;
            }
        }

        for (PassoItinerarioDTO passo : itinerario.passos()) {
            if (!passo.idMotorista().equals(motoristaAtual)) {
                motoristaAtual = passo.idMotorista();
                corIndex = (corIndex + 1) % cores.length;
            }

            Cidade origem = encontrarCidade(passo.inicio());
            Cidade destino = encontrarCidade(passo.fim());

            if (origem != null && destino != null) {
                Line linha = new Line(origem.x, origem.y, destino.x, destino.y);
                linha.getStyleClass().add("rota-motorista-linha");
                linha.setStyle(String.format("-fx-stroke: %s; -fx-stroke-width: 7;", cores[corIndex]));
                mapaGrupo.getChildren().add(indexInsercao, linha);
            }
        }
    }

    private void reaplicarDestaquesBusca() {
        if (modoAtual != ModoTela.BUSCA) return;

        for (Node node : mapaGrupo.getChildren()) {
            if (node instanceof Circle circulo && circulo.getUserData() instanceof Cidade c) {
                if (c.equals(cidadeOrigemBusca) && !circulo.getStyleClass().contains("cidade-origem")) {
                    circulo.getStyleClass().add("cidade-origem");
                }
                if (c.equals(cidadeDestinoBusca) && !circulo.getStyleClass().contains("cidade-destino")) {
                    circulo.getStyleClass().add("cidade-destino");
                }
            }
        }
    }


    // ==========================================
    // 5. MODO: EXIBIÇÃO DE DETALHES
    // ==========================================
    private void carregarDadosCaronaExibicao(Object dadosCarona) {
        int vagasTotais = 4;
        List<TrechoOcupacaoDTO> ocupacoes = new ArrayList<>();

        lblExibeMotorista.setText("Motorista: ");
        lblExibeDataHora.setText("Partida: ");
        listViewPercursoExibicao.getItems().clear();
        List<Trecho> rotaParaDesenhar = new ArrayList<>();

        if (!ocupacoes.isEmpty()) {
            for (TrechoOcupacaoDTO toc : ocupacoes) {
                rotaParaDesenhar.add(toc.trecho());
                int vagasNesteTrecho = vagasTotais - toc.passageiros().size();
                Cidade origem = encontrarCidade(toc.trecho().inicio());
                Cidade destino = encontrarCidade(toc.trecho().fim());

                if (origem != null && destino != null) {
                    String texto = origem.nome + " ➔ " + destino.nome + " (" + vagasNesteTrecho + " vagas restando)";
                    listViewPercursoExibicao.getItems().add(texto);
                }
            }
        }
        desenharRota(rotaParaDesenhar);
    }

    private void desenharRota(List<Trecho> trechos) {
        limparMapaDinamico();
        if (trechos == null || trechos.isEmpty()) return;

        for (Trecho trecho : trechos) {
            Cidade origem = encontrarCidade(trecho.inicio());
            Cidade destino = encontrarCidade(trecho.fim());

            if (origem != null && destino != null) {
                Line linha = new Line(origem.x, origem.y, destino.x, destino.y);
                linha.getStyleClass().add("rota-motorista-linha");
                linha.setStyle("-fx-stroke: #2980b9; -fx-stroke-width: 4;");
                mapaGrupo.getChildren().add(0, linha);
            }
        }
    }


    // ==========================================
    // 6. MÉTODOS UTILITÁRIOS
    // ==========================================
    private void limparMapaDinamico() {
        mapaGrupo.getChildren().removeIf(node -> node.getStyleClass().contains("rota-motorista-linha"));
        for (Node node : mapaGrupo.getChildren()) {
            if (node instanceof Circle) {
                node.getStyleClass().removeAll("cidade-selecionada", "cidade-origem", "cidade-destino");
            }
        }
        rotaMotorista.clear();
    }

    private Cidade encontrarCidade(int idCidade) {
        return cidades.stream().filter(c -> c.id == idCidade).findFirst().orElse(null);
    }

    public void receberRespostaReserva(GeneralResponse resposta) {
        Platform.runLater(() -> {
            painelLoading.setVisible(false);
            painelLoading.setManaged(false);

            if (resposta.codigo() >= 200 && resposta.codigo() < 300) {
                if ("RESERVA_CONFIRMADA".equals(resposta.tipo()) || resposta.dados().contains("RESERVA_CONFIRMADA")) {
                    Toast.success("Reserva confirmada com sucesso!");
                    SceneManager.pop();
                } else {
                    Toast.warning("Conflito: " + resposta.dados());
                }
            } else {
                Toast.error("Erro ao reservar: " + resposta.dados());
            }
        });
    }

    @FXML
    void voltar(ActionEvent event) {
        SceneManager.pop();
    }
}