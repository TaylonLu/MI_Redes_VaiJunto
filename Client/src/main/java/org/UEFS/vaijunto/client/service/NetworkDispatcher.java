package org.UEFS.vaijunto.client.service;

import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

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
