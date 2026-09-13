package org.UEFS.client.controller;

import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.UEFS.client.utils.Toast;

public class TelaInicialController {

    @FXML private TabPane tabPanePrincipal;
    @FXML private Tab tabCliente;
    @FXML private ListView<String> listaCaronasInscritas;

    @FXML private Tab tabMotorista;
    @FXML private ListView<String> listaCaronasCriadas;

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

    // Simulação do estado do usuário
    private boolean isUsuarioMotorista = false;

    @FXML
    public void initialize() {
        // Mock de dados do usuário logado
        lblNome.setText("Nome: João da Silva");
        lblEmail.setText("Email: joao@uefs.br");



        // Mock populando as listas para demonstração
        listaCaronasInscritas.getItems().addAll("Feira -> Salvador (20/09) - João", "Feira -> Serrinha (22/09) - Maria");
        listaCaronasCriadas.getItems().addAll("Feira -> SSA (25/09) - 3 vagas");

        atualizarVisualizacaoInterface();
    }

    private void atualizarVisualizacaoInterface() {
        if (isUsuarioMotorista) {
            lblStatus.setText("Status: Motorista");

            // Oculta área de cadastro e exibe os dados do veículo
            btnCadastrarMotorista.setVisible(false);
            painelFormularioMotorista.setVisible(false);
            painelVeiculoSalvo.setVisible(true);

            // Garante que a aba do motorista exista
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
        System.out.println("Ação: Redirecionando para MapaCarona.fxml em modo BUSCA...");
    }

    @FXML
    void cancelarInscricao(ActionEvent event) {
        String caronaSelecionada = listaCaronasInscritas.getSelectionModel().getSelectedItem();

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
        System.out.println("Ação: Redirecionando para MapaCarona.fxml em modo CRIACAO...");
    }

    @FXML
    void cancelarCaronaCriada(ActionEvent event) {
        String caronaSelecionada = listaCaronasCriadas.getSelectionModel().getSelectedItem();

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
            SceneManager.push("/FXML/Inicio.fxml", "Tela de Inicio");
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