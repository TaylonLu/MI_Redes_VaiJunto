package org.UEFS.client.Controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.UEFS.client.utils.Toast;

import java.io.IOException;

public class InicioController {

    @FXML
    private Button btnCadastro;

    @FXML
    private Button btnLogin;

    @FXML
    void irParaCadastro(ActionEvent event) {
        SceneManager.mudarCena("/FXML/Cadastro.fxml", "VaiJunto - Login");
    }

    @FXML
    void irParaLogin(ActionEvent event) {
        SceneManager.mudarCena("/FXML/Login.fxml", "VaiJunto - Cadastro");
    }

    private void carregarCena(ActionEvent event, String fxmlPath, String titulo) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setTitle(titulo);
            stage.setScene(new Scene(root));
            Toast.install(stage);
            stage.show();
            Toast.info("Olá");
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Erro ao carregar a tela: " + fxmlPath);
        }
    }

}
