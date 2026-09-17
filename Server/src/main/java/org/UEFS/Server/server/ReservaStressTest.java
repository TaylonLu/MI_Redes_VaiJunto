package org.UEFS.Server.server;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ReservaStressTest {
    private static final String HOST = "192.168.0.107";
    private static final int PORTA = 2602;
    private static final int NUM_CLIENTES = 50;

    // Carona real e ativa extraída do seu caronas.json
    private static final String JSON_ITINERARIO =
            "{\"passos\":[{\"inicio\":22,\"fim\":21,\"idCarona\":\"carona_c22ab997-32fe-48e3-92c4-0f63d9a70fcc\",\"idMotorista\":\"user_685291de-fe77-4eb3-9892-7d9ce9878e37\"}]}";

    public static void main(String[] args) throws Exception {
        String[] tokens = new String[NUM_CLIENTES];

        System.out.println("Pré-requisito: Efetuando login de " + NUM_CLIENTES + " clientes...");

        // 1. PREPARAÇÃO SEQUENCIAL (Faz login de cada cliente para obter o token)
        for (int i = 0; i < NUM_CLIENTES; i++) {
            String email = "stress_" + i + "@teste.com";
            String senha = "123";

            String jsonLogin = String.format("{\"email\":\"%s\",\"senha\":\"%s\"}", email, senha);
            int tamanhoJson = jsonLogin.getBytes(StandardCharsets.UTF_8).length;

            String comandoLogin = String.format("LOGIN|%s|null|%d\n", jsonLogin, tamanhoJson);
            String respostaLogin = enviarRequisicao(comandoLogin);

            tokens[i] = extrairTokenDaResposta(respostaLogin);
            if (tokens[i].equals("TOKEN_FALHO")) {
                System.err.println("Erro crítico: Falha ao obter token no login para o cliente " + i + " -> Resposta: " + respostaLogin);
                return;
            }
        }

        System.out.println("✅ Todos os " + NUM_CLIENTES + " clientes logados e com tokens prontos!");

        // 2. DISPARO CONCORRENTE (O Stress Test real de concorrência nas reservas)
        ExecutorService threadPool = Executors.newFixedThreadPool(NUM_CLIENTES);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(NUM_CLIENTES);

        AtomicInteger sucessos = new AtomicInteger(0);
        AtomicInteger conflitos = new AtomicInteger(0);
        AtomicInteger erros = new AtomicInteger(0);

        for (int i = 0; i < NUM_CLIENTES; i++) {
            final String token = tokens[i];
            final int threadId = i;

            threadPool.execute(() -> {
                try {
                    int sizeReserva = JSON_ITINERARIO.getBytes(StandardCharsets.UTF_8).length;
                    String cmdReserva = String.format("CONFIRMAR_RESERVA|%s|%s|%d\n", JSON_ITINERARIO, token, sizeReserva);

                    // Aguarda o tiro de largada
                    startLatch.await();

                    // Disparo simultâneo na porta 2602!
                    String respostaReserva = enviarRequisicao(cmdReserva);

                    if (respostaReserva.contains("RESERVA_CONFIRMADA")) {
                        sucessos.incrementAndGet();
                    } else if (respostaReserva.contains("CONFLITO_RESERVA")) {
                        conflitos.incrementAndGet();
                    } else {
                        erros.incrementAndGet();
                        System.out.println("Thread " + threadId + " retornou: " + respostaReserva);
                    }
                } catch (Exception e) {
                    erros.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        System.out.println("\nDisparando requisições simultâneas de reserva em 3, 2, 1... GO!");
        long startTime = System.currentTimeMillis();

        // TIRO DE LARGADA
        startLatch.countDown();

        doneLatch.await();
        long tempoTotal = System.currentTimeMillis() - startTime;
        threadPool.shutdown();

        System.out.println("\n=== RESULTADO DO STRESS TEST ===");
        System.out.println("Tempo total de processamento: " + tempoTotal + "ms");
        System.out.println("Reservas confirmadas (deve igualar o número de vagas da carona): " + sucessos.get());
        System.out.println("Conflitos prevenidos com sucesso (bloqueados pelo synchronized): " + conflitos.get());
        System.out.println("Erros de execução: " + erros.get());
    }

    private static String enviarRequisicao(String comando) throws Exception {
        try (Socket socket = new Socket(HOST, PORTA);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            socket.setSoTimeout(15000);
            out.write(comando.getBytes(StandardCharsets.UTF_8));
            out.flush();

            byte[] buffer = new byte[4096];
            int bytesRead = in.read(buffer);
            if (bytesRead > 0) {
                return new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
            }
            return "";
        }
    }

    private static String extrairTokenDaResposta(String respostaLogin) {
        try {
            String[] partes = respostaLogin.split("\\|");
            if (partes.length >= 3) {
                String jsonDados = partes[2];
                String chave = "\"token\":\"";
                int inicio = jsonDados.indexOf(chave);
                if (inicio != -1) {
                    inicio += chave.length();
                    int fim = jsonDados.indexOf("\"", inicio);
                    if (fim != -1) {
                        return jsonDados.substring(inicio, fim);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao extrair token: " + respostaLogin);
        }
        return "TOKEN_FALHO";
    }
}