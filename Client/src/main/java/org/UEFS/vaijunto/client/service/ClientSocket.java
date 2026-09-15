package org.UEFS.vaijunto.client.service;

import org.UEFS.vaijunto.client.MainClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientSocket {
    private static ClientSocket instance;
    private BufferedReader reader;
    private PrintWriter writer;
    private Socket socket;

    private ClientSocket() {}

    public synchronized static ClientSocket getInstance() {
        if (instance == null)
            instance = new ClientSocket();

        return instance;
    }

    public synchronized void conectar() {
        conectar(MainClient.IP_SERVIDOR, MainClient.PORTA_SERVIDOR);
    }

    public synchronized void conectar(String host, int port) {
        if (socket == null  || socket.isClosed()) {
            try {
                socket = new Socket(host, port);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                writer = new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            System.out.printf("Socker não é nulo ou não está fechado...");
        }
    }

    public synchronized void desconectar() throws IOException {
        try {
            if (writer != null) writer.println("SAIR");
            if (socket != null && !socket.isClosed())
                socket.close();
        } catch (IOException _) {
        } finally {
            socket = null;
            writer = null;
            reader = null;
        }
    }

    public synchronized void enviarComando(String comando) throws IOException {
        if (socket == null || socket.isClosed()) {
            throw new IOException("Socket não está conectado ao servidor.");
        }

        writer.println(comando);

        if (comando.equals("SAIR")) {
            desconectar();
        }
    }

    public boolean connected() {
        return (socket != null && !socket.isClosed());
    }

    public BufferedReader getReader() {
        return reader;
    }

    public PrintWriter getWriter() {
        return writer;
    }
}
