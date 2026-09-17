package org.UEFS.vaijunto.client.controller;

import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.UEFS.shared.JsonUtils;
import org.UEFS.shared.dto.PerfilMotoristaDTO;
import org.UEFS.shared.dto.SelfUserDTO;
import org.UEFS.shared.dto.requests.CadastroMotoristaRequest;
import org.UEFS.shared.dto.requests.GeneralRequest;
import org.UEFS.shared.dto.responses.CaronaResponse;
import org.UEFS.shared.dto.responses.ReservaResponse;
import org.UEFS.vaijunto.client.service.ClientRouter;
import org.UEFS.vaijunto.client.service.NetworkDispatcher;
import org.UEFS.vaijunto.client.service.SessionManager;
import org.UEFS.vaijunto.client.utils.Tela;
import org.UEFS.vaijunto.client.utils.Toast;
import org.UEFS.vaijunto.client.view.CaronaListCell; // ajuste o pacote conforme seu projeto
import org.UEFS.vaijunto.client.view.ReservaListCell; // ajuste o pacote conforme seu projeto

import java.util.List;

public class TelaInicialController {

    @FXML private TabPane tabPanePrincipal;
    @FXML private Tab tabCliente;
    @FXML private ListView<ReservaResponse> listaCaronasInscritas;

    @FXML private Tab tabMotorista;
    @FXML private ListView<CaronaResponse> listaCaronasCriadas;

    @FXML private Tab tabPerfil;

    // DADOS USUÁRIO
    @FXML private Label lblNome;
    @FXML private Label lblEmail;
    @FXML private Label lblStatus;

    // DADOS DO MOTORISTA
    @FXML private Label lblModelo;
    @FXML private Label lblPlaca;
    @FXML private Label lblCor;
    @FXML private Label lblCnh;

    // Elementos de Gestão do Veículo
    @FXML private Button btnCadastrarMotorista;
    @FXML private VBox painelFormularioMotorista;
    @FXML private TextField txtCnhCarro;
    @FXML private TextField txtModeloCarro;
    @FXML private TextField txtCorCarro;
    @FXML private TextField txtPlacaCarro;

    @FXML private VBox painelVeiculoSalvo;
    @FXML private Label lblVeiculoInfo;

    private final SessionManager sessionManager = SessionManager.getInstance();
    private SelfUserDTO usuarioLogado;
    private boolean isUsuarioMotorista = false;

    @FXML
    public void initialize() {
        usuarioLogado = sessionManager.getCurrentUser();

        if (usuarioLogado == null) SceneManager.clearAndPush(Tela.TELA_INICIO);

        isUsuarioMotorista = usuarioLogado.perfilMotorista() != null;

        // Célula customizada para cada tipo de lista. O duplo clique para abrir o
        // popup de detalhes é tratado dentro das próprias células (via getItem())
        listaCaronasInscritas.setCellFactory(lv -> new ReservaListCell());
        listaCaronasCriadas.setCellFactory(lv -> new CaronaListCell());

        ClientRouter.inscrever("MINHAS_CARONAS", res -> {
            List<CaronaResponse> caronasEncontradas = JsonUtils.fromJsonList(res.dados(), CaronaResponse.class);
            listaCaronasCriadas.getItems().setAll(caronasEncontradas);
        });

        ClientRouter.inscrever("RESERVAS_PASSAGEIRO", res -> {
            List<ReservaResponse> reservasEncontradas = JsonUtils.fromJsonList(res.dados(), ReservaResponse.class);
            listaCaronasInscritas.getItems().setAll(reservasEncontradas);
        });

        atualizarVisualizacaoInterface();

        tabPanePrincipal.sceneProperty().addListener((_, _, newSCene) -> {
            if (newSCene != null) atualizarDados();
        });
    }

    private void atualizarDadosUser() {
        if (isUsuarioMotorista) {
            PerfilMotoristaDTO motorista = sessionManager.getCurrentUser().perfilMotorista();
            lblCnh.setText(motorista.cnh());
            lblModelo.setText(motorista.modeloCarro());
            lblPlaca.setText(motorista.placaCarro());
            lblCor.setText(motorista.corCarro());
        }
    }

    private void atualizarVisualizacaoInterface() {
        if (isUsuarioMotorista) {
            lblStatus.setText("Motorista");

            btnCadastrarMotorista.setVisible(false);
            painelFormularioMotorista.setVisible(false);
            painelVeiculoSalvo.setVisible(true);

            if (!tabPanePrincipal.getTabs().contains(tabMotorista)) {
                tabPanePrincipal.getTabs().add(1, tabMotorista);
            }
        } else {
            lblStatus.setText("Passageiro");

            // Exibe botão de intenção de cadastro, oculta o resto.
            btnCadastrarMotorista.setVisible(true);
            painelFormularioMotorista.setVisible(false);
            painelVeiculoSalvo.setVisible(false);

            // Oculta aba do motorista
            tabPanePrincipal.getTabs().remove(tabMotorista);
        }
    }

    private void atualizarDados() {
        if (isUsuarioMotorista) {
            String minhasCaronas = String.format("MINHAS_CARONAS|%s|%s|%d", "", sessionManager.getUserToken(), 0);
            NetworkDispatcher.enviarComando(minhasCaronas);
        }

        String minhasReservas = String.format("MINHAS_RESERVAS||%s|%d", sessionManager.getUserToken(), 0);
        NetworkDispatcher.enviarComando(minhasReservas);
    }

    // ==========================================
    // AÇÕES DA ÁREA DO CLIENTE
    // ==========================================
    @FXML
    void procurarCarona(ActionEvent event) {
        SceneManager.push(Tela.TELA_MAPA);

        MapaCaronaController mapaCaronaController = (MapaCaronaController) SceneManager.getCurrentController();
        mapaCaronaController.configurarModo(MapaCaronaController.ModoTela.BUSCA, null);
    }

    @FXML
    void cancelarInscricao(ActionEvent event) {
        ReservaResponse reservaSelecionada = listaCaronasInscritas.getSelectionModel().getSelectedItem();

        if (reservaSelecionada != null) {
            // Aqui enviaria a requisição de cancelamento para o servidor via Socket
            System.out.println("Cancelando reserva: " + reservaSelecionada);

            String dados = JsonUtils.toJson(reservaSelecionada);
            String requisicao = String.format("CANCELAR_RESERVA|%s|%s|%d", dados, sessionManager.getUserToken(), dados.length());

            NetworkDispatcher.enviarComando(requisicao);

            listaCaronasInscritas.getItems().remove(reservaSelecionada);
            mostrarAlerta("Sucesso", "Inscrição cancelada com sucesso!");
        } else {
            mostrarAlerta("Aviso", "Selecione uma reserva na lista para cancelar sua inscrição.");
        }
    }

    // ==========================================
    // AÇÕES DA ÁREA DO MOTORISTA
    // ==========================================
    @FXML
    void criarCarona(ActionEvent event) {
        SceneManager.push(Tela.TELA_MAPA);

        MapaCaronaController mapaCaronaController = (MapaCaronaController) SceneManager.getCurrentController();
        mapaCaronaController.configurarModo(MapaCaronaController.ModoTela.CRIACAO, null);
    }

    @FXML
    void cancelarCaronaCriada(ActionEvent event) {
        CaronaResponse caronaSelecionada = listaCaronasCriadas.getSelectionModel().getSelectedItem();

        if (caronaSelecionada != null) {
            // Aqui enviaria a requisição de cancelamento para o servidor
            String requisicao = String.format("CANCELAR_CARONA|%s|%s|%d",
                    caronaSelecionada.id(),
                    sessionManager.getUserToken(),
                    caronaSelecionada.id().length()
            );

            NetworkDispatcher.enviarComando(requisicao);

            listaCaronasCriadas.getItems().remove(caronaSelecionada);
            mostrarAlerta("Sucesso", "Viagem cancelada e passageiros notificados (no backend)!");
        } else {
            mostrarAlerta("Aviso", "Selecione uma viagem na lista para cancelar.");
        }
    }

    // ==========================================
    // GESTÃO DE PERFIL E VEÍCULO
    // ==========================================
    @FXML
    void mostrarFormularioMotorista(ActionEvent event) {
        btnCadastrarMotorista.setVisible(false);
        painelFormularioMotorista.setVisible(true);
    }

    @FXML
    void cancelarFormularioMotorista(ActionEvent event) {
        painelFormularioMotorista.setVisible(false);
        btnCadastrarMotorista.setVisible(true);
        txtModeloCarro.clear();
        txtCorCarro.clear();
        txtPlacaCarro.clear();
    }

    @FXML
    void confirmarCadastroMotorista(ActionEvent event) {
        String modelo = txtModeloCarro.getText().trim();
        String cor = txtCorCarro.getText().trim();
        String placa = txtPlacaCarro.getText().trim();
        String cnh = txtCnhCarro.getText().trim();

        if (modelo.isEmpty() || cor.isEmpty() || placa.isEmpty()) {
            mostrarAlerta("Erro", "Preencha todos os campos do veículo.");
            return;
        }

        System.out.println("Registrando motorista com carro: " + modelo + ", " + cor + ", " + placa);

        CadastroMotoristaRequest cadastro = new CadastroMotoristaRequest(cnh, placa, modelo, cor);
        String dados = JsonUtils.toJson(cadastro);
        String request = GeneralRequest.toMessage(
                "CADASTRO_MOTORISTA",
                dados,
                sessionManager.getUserToken(),
                dados.length()
        );

        EventHandler<WorkerStateEvent> onSucceeded = E -> {
            this.isUsuarioMotorista = true;
            atualizarVisualizacaoInterface();
            tabPanePrincipal.getSelectionModel().select(tabMotorista);
            mostrarAlerta("Parabéns", "Você agora é um motorista!");
        };

        EventHandler<WorkerStateEvent> onFailed = E -> {
            Toast.warning("Não foi possível completar o cadastro como motorista.");
        };

        NetworkDispatcher.enviarComando(request, onSucceeded, onFailed);
    }

    // ==========================================
    // LOGOUT
    // ==========================================
    @FXML
    void fazerLogout(ActionEvent event) {
        System.out.println("Limpando sessão do usuário e voltando para Login.fxml...");

        EventHandler<WorkerStateEvent> onSucceded = A -> {
            Toast.info("Fazendo logout...");
            NetworkDispatcher.limparLogin();
            SceneManager.clearAndPush(Tela.TELA_INICIO);
        };

        NetworkDispatcher.enviarComando("SAIR", onSucceded);
    }

    // ==========================================
    // UTILITÁRIO
    // ==========================================
    private void mostrarAlerta(String titulo, String mensagem) {
        Alert alerta = new Alert(Alert.AlertType.INFORMATION);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensagem);
        alerta.showAndWait();
    }
}