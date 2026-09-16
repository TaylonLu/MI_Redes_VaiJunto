package org.UEFS.vaijunto.client.service;

import javafx.application.Platform;
import org.UEFS.shared.enums.Status;
import org.UEFS.vaijunto.client.controller.SceneManager;
import org.UEFS.vaijunto.client.utils.Tela;
import org.UEFS.vaijunto.client.utils.Toast;
import org.UEFS.shared.dto.responses.GeneralResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ClientRouter {
    private final Map<String, Consumer<GeneralResponse>> rotas = new HashMap<>();
    private static final Map<String, Consumer<GeneralResponse>> ouvintesDaInterface = new HashMap<>();

    Consumer<GeneralResponse> acaoPadrao = res -> {
        Toast.warning("Comando não implementado: " + res.tipo());
    };

    public ClientRouter() {
        configurarRotas();
    }

    public static void inscrever(String tipoComando, Consumer<GeneralResponse> acao) {
        ouvintesDaInterface.put(tipoComando, acao);
    }
    public static void removerInscricao(String tipoComando) {
        ouvintesDaInterface.remove(tipoComando);
    }

    private void configurarRotas() {
        rotas.put(Status.LOGIN_EFETUADO.getTipo(), res -> {
            Toast.success("Login efetuado.");
            AuthHandler.guardarLogin(res);
            SceneManager.clearAndPush(Tela.TELA_PRINCIPAL);
        });

        rotas.put(Status.CADASTRO_COMPLETO.getTipo(), res -> {
            Toast.success("Cadastro feito.");
            AuthHandler.guardarLogin(res);
            SceneManager.clearAndPush(Tela.TELA_PRINCIPAL);
        });
    }


    public void processarMensagem(String rawM) {
        System.out.println("RESPOSTA SERVIDOR: " + rawM);

        try {
            GeneralResponse response = GeneralResponse.fromString(rawM);
            System.out.println(response);

            if (response.codigo() >= 700) {
                Platform.runLater(() -> Toast.error(rawM));
                return;
            }

            Consumer<GeneralResponse> acaoFixa = rotas.get(response.tipo());
            if (acaoFixa != null) {
                Platform.runLater(() -> acaoFixa.accept(response));
                return;
            }

            Consumer<GeneralResponse> acaoTela = ouvintesDaInterface.getOrDefault(response.tipo(), acaoPadrao);
            if (acaoTela != null) {
                Platform.runLater(() -> acaoTela.accept(response));
            }
        } catch (Exception e) {
            System.out.println("Erro: \n" + e);
            System.out.println(rawM);
        }
    }
}
