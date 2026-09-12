package org.UEFS.client.Controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;

public class InicioController {

    @FXML
    private Button btnCadastro;

    @FXML
    private Button btnLogin;

    @FXML
    void irParaCadastro(ActionEvent event) {
        SceneManager.push("/FXML/Cadastro.fxml", "VaiJunto - Login");
    }

    @FXML
    void irParaLogin(ActionEvent event) {
        SceneManager.push("/FXML/Login.fxml", "VaiJunto - Cadastro");
    }
}
