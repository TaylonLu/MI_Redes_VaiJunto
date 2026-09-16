package org.UEFS.vaijunto.client.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import org.UEFS.vaijunto.client.service.ClientSocket;
import org.UEFS.vaijunto.client.utils.Tela;

public class InicioController {

    @FXML
    private Button btnCadastro;

    @FXML
    private Button btnLogin;

    @FXML
    public void initialize() {
        btnCadastro.sceneProperty().addListener((_, _, newScene) -> {
            if (newScene != null) {
                if (!ClientSocket.getInstance().connected()) {
                    ClientSocket.getInstance().conectar();
                }
            }
        });
    }


    @FXML
    void irParaCadastro(ActionEvent event) {
        if (!ClientSocket.getInstance().connected()) {
            ClientSocket.getInstance().conectar();
        }

        SceneManager.push(Tela.TELA_CADASTRO);
    }

    @FXML
    void irParaLogin(ActionEvent event) {
        if (!ClientSocket.getInstance().connected()) {
            ClientSocket.getInstance().conectar();
        }

        SceneManager.push(Tela.TELA_LOGIN);
    }
}
