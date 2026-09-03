package org.UEFS.vaijunto.Server;
import org.UEFS.vaijunto.Exceptions.Status;
import org.UEFS.vaijunto.Util.DmppParser;
import org.UEFS.vaijunto.Util.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.rmi.ServerException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.UEFS.vaijunto.Util.DmppParser.getServerIP;

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
                cSocket.setSoTimeout(60_000);
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
                    break;
                }

                Request request;

                try { request = DmppParser.parceRequest(requisicao);
                } catch (ServerException e) {
                    saida.write(new Response(Status.BAD_REQUEST).toMessage());
                    break;
                }

                IOUtils.fprintln("[:yellow]=== REQUISIÇÃO DO CLIENTE ===[::]");
                System.out.println(request);
                IOUtils.fprintln("[:yellow]=== ========================= ===[::]");

                Response response = router.processar(request);
                saida.println(response.toMessage());
            }
        } catch (SocketTimeoutException e){
            IOUtils.fprintln("[:yellow]Conexão com o cliente chegou ao limite.");
        } catch (IOException e) {
            try { client.close(); } catch (IOException ignored) {} throw new RuntimeException(e);
        } finally {
           try {
               if (!client.isClosed()) client.close();
           } catch (IOException e) {
               IOUtils.fprintln("[:red]Erro ao fechar socket do cliente.[::]");
           }
            System.out.println("Resposta enviada e conexão fechada.\n");
        }
    }

}