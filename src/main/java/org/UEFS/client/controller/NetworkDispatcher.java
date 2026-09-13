package org.UEFS.client.controller;

import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import org.UEFS.client.service.ClientSocket;
import org.UEFS.client.service.SessionManager;
import org.UEFS.shared.ServerGrammar;

import java.util.zip.DataFormatException;

public class NetworkDispatcher {
    private static final SessionManager sessionManager = SessionManager.getInstance();
    private static final ClientSocket socket = ClientSocket.getInstance();

    private static Task<Void> criarTarefa(String comando) {
        return new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                socket.enviarComando(comando);
                return null;
            }
        };
    }
    public static void guardarLogin(String rawData) throws DataFormatException {
        String[] dadosSplit = ServerGrammar.extrairEnvelope(rawData);
        if (dadosSplit.length != 4) throw new DataFormatException("Dados formatados incorretamente.");

        String dados = dadosSplit[2];

        sessionManager.setUserToken(dados);
    }

    public static void enviarComando(String comando) {
        Task<Void> task = criarTarefa(comando);
        new Thread(task).start();
    }

    public static void enviarComando(String comando, EventHandler<WorkerStateEvent> onSucceeded) {
        Task<Void> tarefaDeRede = criarTarefa(comando);

        tarefaDeRede.setOnSucceeded(onSucceeded);

        new Thread(tarefaDeRede).start();
    }

    public static void enviarComando(String comando, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFail) {
        Task<Void> tarefaDeRede = criarTarefa(comando);

        tarefaDeRede.setOnSucceeded(onSucceeded);
        tarefaDeRede.setOnFailed(onFail);

        new Thread(tarefaDeRede).start();
    }

    public static void limparLogin() {
        sessionManager.limparSessao();
    }
}
