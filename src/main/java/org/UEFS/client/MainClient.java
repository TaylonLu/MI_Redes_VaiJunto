package org.UEFS.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import jdk.jshell.spi.ExecutionControl;
import org.UEFS.client.Controller.SceneManager;
import org.UEFS.client.Service.ClientListener;
import org.UEFS.client.Service.ClientRouter;
import org.UEFS.client.Service.ClientSocket;
import org.w3c.dom.CDATASection;

import java.io.BufferedReader;
import java.io.IOException;

public class MainClient extends Application {
    public static void main(String[] args) {
        launch(args);
    }
    @Override
    public void start(Stage primaryStage) throws Exception {
        String inicio = "/FXML/Inicio.fxml";

        ClientSocket CSocket = ClientSocket.getInstance();

        try {
            CSocket.conectar("localhost", 2602);
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
        SceneManager.mudarCena(inicio, "VaiJunto - Início");
    }

    @Override
    public void stop() throws Exception {
        ClientSocket.getInstance().desconectar();
        super.stop();
    }
}
