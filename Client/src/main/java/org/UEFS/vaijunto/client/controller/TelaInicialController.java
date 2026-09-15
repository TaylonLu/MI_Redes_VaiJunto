package org.UEFS.vaijunto.client.controller;

import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.UEFS.shared.JsonUtils;
import org.UEFS.shared.dto.SelfUserDTO;
import org.UEFS.shared.dto.responses.CaronaResponse;
import org.UEFS.vaijunto.client.service.ClientRouter;
import org.UEFS.vaijunto.client.service.NetworkDispatcher;
import org.UEFS.vaijunto.client.service.SessionManager;
import org.UEFS.vaijunto.client.utils.Tela;
import org.UEFS.vaijunto.client.utils.Toast;

import java.util.List;

public class TelaInicialController {

    @FXML private TabPane tabPanePrincipal;
    @FXML private Tab tabCliente;
    @FXML private ListView<CaronaResponse> listaCaronasInscritas;

    @FXML private Tab tabMotorista;
    @FXML private ListView<CaronaResponse> listaCaronasCriadas;

    @FXML private Tab tabPerfil;
    @FXML private Label lblNome;
    @FXML private Label lblEmail;
    @FXML private Label lblStatus;

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
        isUsuarioMotorista = usuarioLogado != null && usuarioLogado.perfilMotorista() != null;

        lblNome.setText(usuarioLogado.nome());
        lblEmail.setText(usuarioLogado.email());

        ClientRouter.inscrever("MINHAS_CARONAS", res -> {
            List<CaronaResponse> caronasEncontradas = JsonUtils.fromJsonList(res.dados(), CaronaResponse.class);
            listaCaronasCriadas.getItems().addAll(caronasEncontradas);
        });

        ClientRouter.inscrever("MINHAS_RESERVAS", res -> {
            List<CaronaResponse> caronasEncontradas = JsonUtils.fromJsonList(res.dados(), CaronaResponse.class);
            listaCaronasInscritas.getItems().addAll(caronasEncontradas);
        });

        String minhasCaronas = String.format("MINHAS_CARONAS|%s|%s|%d", "", sessionManager.getUserToken(), 0);
        NetworkDispatcher.enviarComando(minhasCaronas);

        atualizarVisualizacaoInterface();
    }

    private void atualizarVisualizacaoInterface() {
        if (isUsuarioMotorista) {
            lblStatus.setText("Status: Motorista");

            btnCadastrarMotorista.setVisible(false);
            painelFormularioMotorista.setVisible(false);
            painelVeiculoSalvo.setVisible(true);

            if (!tabPanePrincipal.getTabs().contains(tabMotorista)) {
                tabPanePrincipal.getTabs().add(1, tabMotorista);
            }
        } else {
            lblStatus.setText("Status: Passageiro");

            // Exibe botão de intenção de cadastro, oculta o resto
            btnCadastrarMotorista.setVisible(true);
            painelFormularioMotorista.setVisible(false);
            painelVeiculoSalvo.setVisible(false);

            // Oculta aba do motorista
            tabPanePrincipal.getTabs().remove(tabMotorista);
        }
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
        CaronaResponse caronaSelecionada = listaCaronasInscritas.getSelectionModel().getSelectedItem();

        if (caronaSelecionada != null) {
            // Aqui enviaria a requisição de cancelamento para o servidor via Socket
            System.out.println("Cancelando inscrição na carona: " + caronaSelecionada);
            listaCaronasInscritas.getItems().remove(caronaSelecionada);
            mostrarAlerta("Sucesso", "Inscrição cancelada com sucesso!");
        } else {
            mostrarAlerta("Aviso", "Selecione uma carona na lista para cancelar sua inscrição.");
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
            System.out.println("Cancelando a viagem criada: " + caronaSelecionada);
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
        // Limpa os campos
        txtModeloCarro.clear();
        txtCorCarro.clear();
        txtPlacaCarro.clear();
    }

    @FXML
    void confirmarCadastroMotorista(ActionEvent event) {
        String modelo = txtModeloCarro.getText();
        String cor = txtCorCarro.getText();
        String placa = txtPlacaCarro.getText();

        if (modelo.isEmpty() || cor.isEmpty() || placa.isEmpty()) {
            mostrarAlerta("Erro", "Preencha todos os campos do veículo.");
            return;
        }

        // Aqui você envia os dados do veículo para o servidor salvar no BD
        System.out.println("Registrando motorista com carro: " + modelo + ", " + cor + ", " + placa);

        lblVeiculoInfo.setText(String.format("Modelo: %s | Cor: %s | Placa: %s", modelo, cor, placa));
        isUsuarioMotorista = true;

        atualizarVisualizacaoInterface();
        tabPanePrincipal.getSelectionModel().select(tabMotorista);
        mostrarAlerta("Parabéns", "Você agora é um motorista!");
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