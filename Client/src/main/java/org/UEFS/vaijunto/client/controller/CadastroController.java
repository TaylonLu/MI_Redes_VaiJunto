package org.UEFS.vaijunto.client.controller;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import org.UEFS.shared.JsonUtils;
import org.UEFS.shared.dto.requests.CadastroRequest;
import org.UEFS.vaijunto.client.service.NetworkDispatcher;
import org.UEFS.vaijunto.client.utils.Tela;
import org.UEFS.vaijunto.client.utils.Toast;

public class CadastroController {
    @FXML private Button btnConf;
    @FXML private TextField userName;
    @FXML private TextField userEmail;
    @FXML private TextField userPassword;
    @FXML private TextField userPasswordConf;
    @FXML private HBox username;

    private boolean carregando = false;

    @FXML
    public void initialize() {
        BooleanBinding senhasDiferentes = Bindings.createBooleanBinding(() -> {
            String senha1 = userPassword.getText();
            String senha2 = userPasswordConf.getText();

            return senha1 == null || senha1.isEmpty() || !senha1.equals(senha2) || carregando;
        }, userPassword.textProperty(), userPasswordConf.textProperty());

        btnConf.disableProperty().bind(senhasDiferentes);

        userPassword.textProperty().addListener(_ -> atualizarCoresDasSenhas());
        userPasswordConf.textProperty().addListener(_ -> atualizarCoresDasSenhas());

    }

    @FXML
    void confirmarDados(ActionEvent event) {
        String senha = userPassword.getText();
        String nome = userName.getText();
        String email = userEmail.getText();


        CadastroRequest cadastroRequest = new CadastroRequest(email, senha, nome);
        String dados = JsonUtils.toJson(cadastroRequest);

        String comando = String.format("CADASTRO|%s|%s|%d", dados, "NULL", dados.length());

        carregando = false;

        Toast.info("Cadastrando...");

        EventHandler<WorkerStateEvent> onSucceeded = _ -> {
            Toast.success("Bem sucedido");
            carregando = true;
        };

        EventHandler<WorkerStateEvent> onFailed = A -> {
            Throwable erro = A.getSource().getException();
            System.err.println("Erro na rede: " + erro.getMessage());
            carregando=true;

            Toast.error("Erro ao conectar no servidor.");
        };

        NetworkDispatcher.enviarComando(comando, onSucceeded, onFailed);
    }

    private void atualizarCoresDasSenhas() {
        String senha = userPassword.getText();
        String confirmacao = userPasswordConf.getText();

        // Se o campo de confirmação estiver vazio, tira os estilos de erro/sucesso para ficar neutro
        if (confirmacao == null || confirmacao.isEmpty()) {
            userPasswordConf.setStyle("");
            return;
        }

        // Se forem iguais, deixa a borda verde (usando a cor verde do seu estilo CSS)
        if (senha.equals(confirmacao)) {
            userPasswordConf.setStyle("-fx-border-color: #2D6A4F; -fx-border-width: 2px; -fx-border-radius: 6px;");
        }
        // Se forem diferentes, deixa a borda vermelha
        else {
            userPasswordConf.setStyle("-fx-border-color: #D32F2F; -fx-border-width: 2px; -fx-border-radius: 6px;");
        }
    }

}
