package org.UEFS.client.Controller;

import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import org.UEFS.client.Service.ClientSocket;
import org.UEFS.client.utils.Toast;

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

         String dados = String.join(";", email, senha);
        System.out.println("Dados: " + dados);

         String comando = String.format("LOGIN|%s|%s|%d", dados, "NULL", dados.length());

        Toast.info("Logando...");
        Task<Void> tarefaDeRede = new Task<>() {
            @Override
            protected Void call() throws Exception {
                ClientSocket.getInstance().enviarComando(comando);
                return null;
            }
        };

        tarefaDeRede.setOnSucceeded(_ -> {
            Toast.success("Mensagem enviada.");
        });

        tarefaDeRede.setOnFailed(_ -> {
            Throwable erro = tarefaDeRede.getException();
            System.err.println("Erro na rede: " + erro.getMessage());

            Toast.error("Erro ao conectar no servidor.");
        });

        new Thread(tarefaDeRede).start();
    }

}
