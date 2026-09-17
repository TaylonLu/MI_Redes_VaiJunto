import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ReservaStressTest {
    private static final String HOST = "172.16.230.27";
    private static final int PORTA = 2602;

    // stress_0 até stress_50
    private static final int NUM_CLIENTES = 50;

    private static final int CAPACIDADE_VAGAS = 3;

    private static final String ID_CARONA =
            "carona_5bbe5089-5910-43cc-9008-a8abdae1fec5";

    private static final String ID_MOTORISTA =
            "user_380dfde6-4515-410b-9594-395d5c4adcb8";

    /*
     * O trecho 1 -> 19 está atualmente vazio na carona.
     *
     * Portanto, as 3 vagas estão disponíveis nesse trecho.
     */
    private static final String JSON_ITINERARIO = String.format(
            "{\"passos\":[{\"inicio\":1,\"fim\":19,\"idCarona\":\"%s\",\"idMotorista\":\"%s\"}]}",
            ID_CARONA,
            ID_MOTORISTA
    );

    public static void main(String[] args) throws Exception {

        String[] tokens = new String[NUM_CLIENTES];

        // ============================================================
        // 1. LOGIN DOS CLIENTES
        // ============================================================

        for (int i = 1; i < NUM_CLIENTES; i++) {

            String email = "stress_"+ i + "@teste.com";
            String senha = "123";

            String jsonLogin = String.format(
                    "{\"email\":\"%s\",\"senha\":\"%s\"}",
                    email,
                    senha
            );

            int tamanhoJson =
                    jsonLogin.getBytes(StandardCharsets.UTF_8).length;

            String comandoLogin = String.format(
                    "LOGIN|%s|null|%d\n",
                    jsonLogin,
                    tamanhoJson
            );

            String respostaLogin =
                    enviarRequisicao(comandoLogin);

            tokens[i] =
                    extrairTokenDaResposta(respostaLogin);

            if (tokens[i].equals("TOKEN_FALHO")) {
                System.err.println(
                        "Falha no login do cliente " + i +
                                ". Resposta: " + respostaLogin
                );

                return;
            }
        }

        // ============================================================
        // 2. PREPARAÇÃO DA CONCORRÊNCIA
        // ============================================================

        ExecutorService threadPool =
                Executors.newFixedThreadPool(NUM_CLIENTES);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        CountDownLatch doneLatch =
                new CountDownLatch(NUM_CLIENTES);

        AtomicInteger sucessos =
                new AtomicInteger(0);

        AtomicInteger conflitos =
                new AtomicInteger(0);

        AtomicInteger erros =
                new AtomicInteger(0);

        // ============================================================
        // 3. CRIAÇÃO DAS THREADS
        // ============================================================

        for (int i = 1; i < NUM_CLIENTES; i++) {

            final String token = tokens[i];
            final int threadId = i;

            threadPool.execute(() -> {

                try {

                    int tamanhoJson =
                            JSON_ITINERARIO
                                    .getBytes(StandardCharsets.UTF_8)
                                    .length;

                    String comandoReserva = String.format(
                            "CONFIRMAR_RESERVA|%s|%s|%d\n",
                            JSON_ITINERARIO,
                            token,
                            tamanhoJson
                    );

                    // Aguarda todas as threads estarem prontas.
                    startLatch.await();

                    String resposta =
                            enviarRequisicao(comandoReserva);

                    String tipoResposta =
                            extrairTipoResposta(resposta);

                    switch (tipoResposta) {

                        case "RESERVA_CONFIRMADA":
                            sucessos.incrementAndGet();
                            break;

                        case "CONFLITO_RESERVA":
                            conflitos.incrementAndGet();
                            break;

                        default:
                            erros.incrementAndGet();

                            System.err.println(
                                    "Thread " + threadId +
                                            " recebeu resposta inesperada: " +
                                            resposta
                            );
                            break;
                    }

                } catch (Exception e) {

                    erros.incrementAndGet();

                    System.err.println(
                            "Thread " + threadId +
                                    " falhou: " + e
                    );

                } finally {

                    doneLatch.countDown();
                }
            });
        }

        // ============================================================
        // 4. DISPARO CONCORRENTE
        // ============================================================

        long inicio =
                System.currentTimeMillis();

        startLatch.countDown();

        doneLatch.await();

        long tempoTotal =
                System.currentTimeMillis() - inicio;

        threadPool.shutdown();

        // ============================================================
        // 5. RESULTADO
        // ============================================================

        int totalSucessos =
                sucessos.get();

        int totalConflitos =
                conflitos.get();

        int totalErros =
                erros.get();

        System.out.println();
        System.out.println("=== RESULTADO DO STRESS TEST ===");
        System.out.println("Clientes: " + NUM_CLIENTES);
        System.out.println("Carona: " + ID_CARONA);
        System.out.println("Trecho testado: 1 -> 19");
        System.out.println("Vagas disponíveis inicialmente: "
                + CAPACIDADE_VAGAS);
        System.out.println("Reservas confirmadas: " + totalSucessos);
        System.out.println("Conflitos: " + totalConflitos);
        System.out.println("Erros: " + totalErros);
        System.out.println("Tempo total: " + tempoTotal + " ms");

        // ============================================================
        // 6. VALIDAÇÃO
        // ============================================================

        boolean passou =
                totalSucessos == CAPACIDADE_VAGAS
                        && totalConflitos == NUM_CLIENTES - CAPACIDADE_VAGAS
                        && totalErros == 0;

        if (passou) {

            System.out.println("Resultado: PASSOU");

        } else {

            System.out.println("Resultado: FALHOU");

            if (totalSucessos > CAPACIDADE_VAGAS) {
                System.out.println(
                        "ERRO: OVERBOOKING DETECTADO."
                );
            }

            if (totalSucessos < CAPACIDADE_VAGAS) {
                System.out.println(
                        "AVISO: Menos reservas foram confirmadas " +
                                "do que o número de vagas disponíveis."
                );
            }

            if (totalErros > 0) {
                System.out.println(
                        "ERRO: Existem requisições que não " +
                                "retornaram uma resposta válida."
                );
            }
        }
    }

// ================================================================
// COMUNICAÇÃO COM O SERVIDOR
// ================================================================

    private static String enviarRequisicao(String comando)
            throws Exception {

        try (Socket socket = new Socket(HOST, PORTA);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            socket.setSoTimeout(15000);

            out.write(
                    comando.getBytes(StandardCharsets.UTF_8)
            );

            out.flush();

            byte[] buffer = new byte[4096];

            int bytesRead = in.read(buffer);

            if (bytesRead > 0) {
                return new String(
                        buffer,
                        0,
                        bytesRead,
                        StandardCharsets.UTF_8
                );
            }

            return "";
        }
    }

// ================================================================
// EXTRAÇÃO DO TIPO DA RESPOSTA
// ================================================================

    private static String extrairTipoResposta(String resposta) {

        if (resposta == null || resposta.isEmpty()) {
            return "";
        }

        /*
         * Protocolo:
         *
         * Status|Tipo|Dados|Tamanho
         */

        String[] partes =
                resposta.split("\\|");

        if (partes.length >= 2) {
            return partes[1].trim();
        }

        return "";
    }

// ================================================================
// EXTRAÇÃO DO TOKEN
// ================================================================

    private static String extrairTokenDaResposta(
            String respostaLogin) {

        try {

            /*
             * Protocolo:
             *
             * Status|Tipo|Dados|Tamanho
             */

            String[] partes =
                    respostaLogin.split("\\|");

            if (partes.length >= 3) {

                String jsonDados =
                        partes[2];

                String chave =
                        "\"token\":\"";

                int inicio =
                        jsonDados.indexOf(chave);

                if (inicio != -1) {

                    inicio += chave.length();

                    int fim =
                            jsonDados.indexOf("\"", inicio);

                    if (fim != -1) {
                        return jsonDados.substring(
                                inicio,
                                fim
                        );
                    }
                }
            }

        } catch (Exception e) {

            System.err.println(
                    "Erro ao extrair token: " +
                            respostaLogin
            );
        }

        return "TOKEN_FALHO";
    }

}
