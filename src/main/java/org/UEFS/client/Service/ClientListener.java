package org.UEFS.client.Service;

import java.io.BufferedReader;
import java.io.IOException;

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
                router.processarMenasagem(mensagemDoServidor);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
