package org.UEFS.client.Service;

import javafx.application.Platform;
import org.UEFS.client.utils.Toast;

import java.util.Arrays;

public class ClientRouter {
    public void processarMenasagem(String rawM) {
        String[] partes = rawM.split("\\|", 3);
        System.out.println(Arrays.toString(partes));

        int codigo = Integer.parseInt(partes[0]);
        String tipo = partes[1];

        if (codigo >= 700 ) {
            Platform.runLater(() -> {
                Toast.error(rawM);
            });
        } else {
            porTipo(rawM, tipo);
        }

    }

    private void porTipo(String rawM, String tipo) {
        switch (tipo) {
            case "LOGIN_EFETUADO" -> Platform.runLater(() -> {
                Toast.success("Login efetuado.");
                SessionManager.getInstance().setUserToken(rawM);
            });
            case "CADASTRO_COMPLETO" -> Platform.runLater(() -> {
                Toast.success("Cadastro feito.");
                // TODO: Chamar controller para mudar a tela.
            });
            default -> Platform.runLater(() -> {
                Toast.warning("Não implementado.");
            });
        }
    }
}
