package org.UEFS.vaijunto.client.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.SocketException;

public class ClientListener implements Runnable {
    private final BufferedReader entrada;
    private final ClientRouter router; // Seu roteador do lado do cliente

    public ClientListener(BufferedReader entrada) {
        this.entrada = entrada;
        this.router = new ClientRouter();
    }

    @Override
    public void run() {
        try {
            String mensagemDoServidor;

            while ((mensagemDoServidor = entrada.readLine()) != null) {
                router.processarMensagem(mensagemDoServidor);
            }
            System.out.println("Listener encerrado.");
        } catch (SocketException e) {
            System.out.println("Conexão com o servidor encerrada (Logout ou Fechamento da aplicação).");
        } catch (IOException e) {
            System.err.println("Erro: \n" + e);
        }
    }
}
