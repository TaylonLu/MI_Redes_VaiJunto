package org.UEFS.vaijunto.client.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import org.UEFS.vaijunto.client.utils.Tela;

public class InicioController {

    @FXML
    private Button btnCadastro;

    @FXML
    private Button btnLogin;

    @FXML
    void irParaCadastro(ActionEvent event) {
        SceneManager.push(Tela.TELA_CADASTRO);
    }

    @FXML
    void irParaLogin(ActionEvent event) {
        SceneManager.push(Tela.TELA_LOGIN);
    }
}
