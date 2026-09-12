package org.UEFS.client;

import javafx.application.Application;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.UEFS.client.Controller.SceneManager;
import org.UEFS.client.Service.ClientListener;
import org.UEFS.client.Service.ClientSocket;

import java.io.BufferedReader;

public class MainClient extends Application {
    public static void main(String[] args) {
        launch(args);
    }
    @Override
    public void start(Stage primaryStage) {
        String inicio = "/FXML/Inicio.fxml";

        ClientSocket CSocket = ClientSocket.getInstance();

        try {
            System.out.println("Conectando ao servidor.");
            CSocket.conectar("localhost", 8080);
            System.out.println("Conectado ao Servidor pelo JavaFX!");
        } catch (Exception e) {
            System.err.println("Não foi possível conectar ao servidor: " + e.getMessage());
        }

        if (CSocket.connected()) {
            BufferedReader reader = CSocket.getReader();
            Thread TListener = new Thread(new ClientListener(reader));
            TListener.setDaemon(true);
            TListener.start();
        }

        SceneManager.inicializar(primaryStage);
        SceneManager.push(inicio, "VaiJunto - Início");
        primaryStage.show();
        primaryStage.getScene().setOnKeyPressed(E -> {
            if (E.getCode() == KeyCode.K) testeMapa();
        });
    }

    private void testeMapa() {
        SceneManager.push("/FXML/MapaCarona.fxml", "Teste");
    }


    @Override
    public void stop() throws Exception {
        ClientSocket.getInstance().desconectar();
        super.stop();
    }
}
