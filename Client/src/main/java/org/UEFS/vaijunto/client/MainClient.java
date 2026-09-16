package org.UEFS.vaijunto.client;

import javafx.application.Application;
import javafx.stage.Stage;
import org.UEFS.vaijunto.client.controller.SceneManager;
import org.UEFS.vaijunto.client.service.ClientListener;
import org.UEFS.vaijunto.client.service.ClientSocket;
import org.UEFS.vaijunto.client.utils.Tela;

import java.io.BufferedReader;

public class MainClient extends Application {
    public static final String IP_SERVIDOR ;
    public static final int PORTA_SERVIDOR;

    static {
        String ip = System.getenv("SERVER_IP");
        String porta = System.getenv("SERVER_PORT");

        if (!porta.matches("^\\d+$"))
            porta = null;

        IP_SERVIDOR = ip != null ? ip : "localhost";
        PORTA_SERVIDOR = porta != null ? Integer.parseInt(porta) : 2602;
    }
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        ClientSocket CSocket = ClientSocket.getInstance();

        try {
            System.out.println("Conectando ao servidor.");
            CSocket.conectar();
            System.out.println("Conectado ao Servidor pelo JavaFX!");
        } catch (Exception e) {
            System.err.println("Não foi possível conectar ao servidor: " + e.getMessage());
        }

        SceneManager.inicializar(primaryStage);
        SceneManager.push(Tela.TELA_INICIO);
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        ClientSocket.getInstance().desconectar();
        super.stop();
    }
}
