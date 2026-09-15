package org.UEFS.vaijunto.client.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import org.UEFS.vaijunto.client.service.NetworkDispatcher;
import org.UEFS.vaijunto.client.utils.Toast;
import org.UEFS.shared.JsonUtils;
import org.UEFS.shared.dto.requests.LoginRequest;

public class LoginController {

    @FXML
    private Button btnConf;

    @FXML
    private TextField userEmail;

    @FXML
    private TextField userPassword;

    @FXML
    void confirmarDados(ActionEvent event) {
         String email = userEmail.getText();
         String senha = userPassword.getText();

         String dados = JsonUtils.toJson(new LoginRequest(email, senha));
         System.out.println("Dados: " + dados);

         String comando = String.format("LOGIN|%s|%s|%d", dados, "NULL", dados.length());

        Toast.info("Logando...");
        NetworkDispatcher.enviarComando(comando, null, WSE -> {
            Throwable erro = WSE.getSource().getException();
            System.err.println("Erro na rede: " + erro.getMessage());

            Toast.error("Erro ao conectar no servidor.");
        });
    }

}
