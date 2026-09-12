package org.UEFS.vaijunto.Server;

import org.UEFS.vaijunto.Exceptions.IncorrectRequestException;
import org.UEFS.vaijunto.Exceptions.ServerException;
import org.UEFS.vaijunto.Exceptions.Status;
import org.UEFS.vaijunto.Util.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.UEFS.vaijunto.Util.Parser.getServerIP;

public class MainServer {
    private final int port;
    private final ExecutorService threadPool;
    private final Router router = new Router();

    public MainServer(int port) {
        this.port = port;
        this.threadPool = Executors.newFixedThreadPool(4);

        Runtime.getRuntime().addShutdownHook(new Thread(this::finish));
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port, 500)) {
            String ip = getServerIP();
            IOUtils.fprintf("[:red]| Servidor iniciado em [:blue]http://%s:%d[::]\n", ip, port);

            for (;;) {
                Socket cSocket = serverSocket.accept();
                cSocket.setSoTimeout(300_000);
                threadPool.submit(() -> handleConnection(cSocket));
            }
        }
    }

    private void finish() {
        this.threadPool.shutdown();
        try {
            if (threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
        }
    }

    private void handleConnection(Socket client) {
        IOUtils.fprintln("[:green]| Novo cliente conectado: " + client.getInetAddress());
        try (client;
             BufferedReader entrada = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter saida = new PrintWriter(client.getOutputStream(), true)
            ) {

            String requisicao;

            while ((requisicao = entrada.readLine()) != null) {
                if (requisicao.equalsIgnoreCase("SAIR")) {
                    System.out.println("Saindo...");
                    break;
                }

                Request request;

                try {
                    request = ServerGrammar.parseRequest(requisicao);
                } catch (IncorrectRequestException e) {
                    saida.println(new Response(Status.BAD_REQUEST.getCodigo(), "", e.getMessage()).toMessage());
                    break;
                } catch (ServerException se){
                    saida.println(se.toResponse());
                    break;
                }

                IOUtils.fprintln("[:yellow]=== REQUISIÇÃO DO CLIENTE ===[::]");
                System.out.println(request);
                IOUtils.fprintln("[:yellow]=== ========================= ===[::]");

                Response response = router.processar(request);
                saida.println(response.toMessage());
            }
        } catch (SocketTimeoutException e){
            IOUtils.fprintln("[:yellow]Conexão com o cliente chegou ao limite por inatividade.[::]");
        } catch (IOException e) {
            IOUtils.fprintln("[:red]| Conexão com o cliente " + client.getInetAddress() + " foi interrompida abruptamente.[::]");
        } finally {
            IOUtils.fprintln("[:green]| Conexão finalizada e recursos liberados.\n[::]");
        }
    }

}