package org.UEFS.client.service;

import javafx.application.Platform;
import org.UEFS.client.utils.Toast;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ClientRouter {
    private final Map<String, Consumer<String>> rotas = new HashMap<>();

    public ClientRouter() {
        configurarRotas();
    }

    private void configurarRotas() {
        rotas.put("LOGIN_EFETUADO", rawM -> {
            Toast.success("Login efetuado.");

        });

        rotas.put("CADASTRO_COMPLETO", rawM -> {
            Toast.success("Cadastro feito.");
            // TODO: Chamar controller para mudar a tela.
        });
    }


    public void processarMensagem(String rawM) {
        String[] partes = rawM.split("\\|", 3);

        System.out.println(Arrays.toString(partes));

        int codigo = Integer.parseInt(partes[0]);
        String tipo = partes[1];

        if (codigo >= 700) {
            Platform.runLater(() -> Toast.error(rawM));
            return;
        }

        Consumer<String> acao = rotas.getOrDefault(tipo, msg -> {
            Toast.warning("Comando não implementado: " + tipo);
        });

        Platform.runLater(() -> acao.accept(rawM));

    }
}
