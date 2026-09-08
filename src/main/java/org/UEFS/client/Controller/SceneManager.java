package org.UEFS.client.Controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.UEFS.client.utils.Resources;
import org.UEFS.client.utils.Toast;

import java.io.IOException;
import java.net.URL;

public class SceneManager {
    private static Stage primaryStage;

    public static void inicializar(Stage stage) {
        primaryStage = stage;
    }

    public static void mudarCena(String fxmlPath, String titulo) {
        try {
            URL url = Resources.get(fxmlPath);

            Parent root = FXMLLoader.load(url);
            Scene scene = new Scene(root);

            primaryStage.setTitle(titulo);
            primaryStage.setScene(scene);
            primaryStage.show();

            Toast.install(primaryStage);

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Erro ao carregar a cena: " + fxmlPath);
        }
    }
}