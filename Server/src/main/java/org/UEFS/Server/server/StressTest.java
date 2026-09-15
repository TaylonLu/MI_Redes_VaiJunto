package org.UEFS.Server.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StressTest {
    // Configurações do Teste
    private static final String IP_SERVIDOR = "localhost";
    private static final int PORTA = 2602;
    private static final int VAGAS_DISPONIVEIS = 2; // Quantidade de vagas reais
    private static final int NUMERO_PASSAGEIROS_CONCORRENTES = 30; // 30 passageiros disputando 2 vagas

    public static void main(String[] args) throws Exception {
        System.out.println("=====================================================");
        System.out.println("  INICIANDO TESTE DE ESTRESSE DE CONCORRÊNCIA (VAIJUNTO)");
        System.out.println("=====================================================\n");

        String uuid = UUID.randomUUID().toString().substring(0, 5); // Evita conflito de e-mail no banco

        // ==========================================
        // FASE 1: PREPARAÇÃO DO MOTORISTA E CARONA
        // ==========================================
        System.out.println("[FASE 1] Preparando Motorista e Carona...");
        String motoristaToken = "";
        String motoristaId = "";

        // 1.1 Cadastra Motorista
        String jsonCadastro = String.format("{\"email\":\"motorista_%s@teste.com\",\"senha\":\"123\",\"nome\":\"Motorista Stress\"}", uuid);
        String resCadastro = enviarRequisicaoSimples("CADASTRO", jsonCadastro, "");
        motoristaToken = extrairValor(resCadastro, "\"token\":\"([^\"]+)\"");
        motoristaId = extrairValor(resCadastro, "\"id\":\"(user_[^\"]+)\"");

        // 1.2 Registra o Perfil de Veículo
        String jsonVeiculo = "{\"cnh\":\"12345678901\",\"placaCarro\":\"ABC1234\",\"modeloCarro\":\"Fusca\",\"corCarro\":\"Azul\"}";
        enviarRequisicaoSimples("CADASTRO_MOTORISTA", jsonVeiculo, motoristaToken);

        // 1.3 Cria a Carona (com VAGAS_DISPONIVEIS)
        String jsonCarona = String.format(
                "{\"idMotorista\":\"%s\",\"capacidade\":%d,\"data\":\"2026-10-10T10:00:00\",\"trechos\":[{\"inicio\":1,\"fim\":2}]}",
                motoristaId, VAGAS_DISPONIVEIS
        );
        enviarRequisicaoSimples("CRIAR_CARONA", jsonCarona, motoristaToken);

        // 1.4 Consulta a carona criada para pegar o ID gerado pelo banco
        String resCaronas = enviarRequisicaoSimples("MINHAS_CARONAS", "", motoristaToken);
        String idCarona = extrairValor(resCaronas, "\"id\":\"(carona_[^\"]+)\"");

        System.out.println(">> Carona criada com SUCESSO! ID: " + idCarona);
        System.out.println(">> Vagas liberadas: " + VAGAS_DISPONIVEIS);

        // ==========================================
        // FASE 2: PREPARAÇÃO DOS PASSAGEIROS
        // ==========================================
        System.out.println("\n[FASE 2] Cadastrando " + NUMERO_PASSAGEIROS_CONCORRENTES + " passageiros simultâneos...");
        List<String> tokensPassageiros = new ArrayList<>();

        for (int i = 0; i < NUMERO_PASSAGEIROS_CONCORRENTES; i++) {
            String jsonPass = String.format("{\"email\":\"pass_%d_%s@teste.com\",\"senha\":\"123\",\"nome\":\"Passageiro %d\"}", i, uuid, i);
            String resPass = enviarRequisicaoSimples("CADASTRO", jsonPass, "");
            tokensPassageiros.add(extrairValor(resPass, "\"token\":\"([^\"]+)\""));
        }
        System.out.println(">> Todos os passageiros estão cadastrados e com Tokens em mãos.");

        // ==========================================
        // FASE 3: O ATAQUE DE ESTRESSE (CONCORRÊNCIA)
        // ==========================================
        System.out.println("\n[FASE 3] Engatilhando Sockets para disparo simultâneo...");

        CountDownLatch travaDeLargada = new CountDownLatch(1);
        CountDownLatch travaDeConclusao = new CountDownLatch(NUMERO_PASSAGEIROS_CONCORRENTES);

        AtomicInteger reservasConfirmadas = new AtomicInteger(0);
        AtomicInteger reservasRejeitadas = new AtomicInteger(0);

        List<Socket> conexoesAbertas = new ArrayList<>();

        for (int i = 0; i < NUMERO_PASSAGEIROS_CONCORRENTES; i++) {
            final String token = tokensPassageiros.get(i);

            // Cria a thread para cada passageiro
            Thread passageiroThread = new Thread(() -> {
                try {
                    Socket socket = new Socket(IP_SERVIDOR, PORTA);
                    conexoesAbertas.add(socket); // Salva para fechar depois

                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    // Payload de Reserva no formato esperado
                    String jsonReserva = String.format("{\"passos\":[{\"idCarona\":\"%s\",\"trecho\":{\"inicio\":1,\"fim\":2}}]}", idCarona);
                    String envelopeDeRede = formatarEnvelope("CONFIRMAR_RESERVA", jsonReserva, token);

                    // A THREAD TRAVA AQUI E FICA ESPERANDO O SINAL VERDE!
                    travaDeLargada.await();

                    // --- O TIRO (Envia o pedido para a rede) ---
                    out.println(envelopeDeRede);

                    // --- A RESPOSTA ---
                    String resposta = in.readLine();
                    if (resposta != null && resposta.contains("RESERVA_CONFIRMADA")) {
                        reservasConfirmadas.incrementAndGet();
                    } else {
                        reservasRejeitadas.incrementAndGet();
                    }

                } catch (Exception e) {
                    System.err.println("Erro na thread do passageiro: " + e.getMessage());
                } finally {
                    travaDeConclusao.countDown();
                }
            });
            passageiroThread.start();
        }

        // Dá um tempinho para garantir que as threads criaram os Sockets
        Thread.sleep(2000);

        System.out.println("\n🚀 DISPARANDO " + NUMERO_PASSAGEIROS_CONCORRENTES + " REQUISIÇÕES AO MESMO TEMPO! 🚀");
        long tempoInicio = System.currentTimeMillis();

        // SINAL VERDE! Libera todas as threads no exato mesmo milissegundo!
        travaDeLargada.countDown();

        // Espera todas as requisições terminarem
        travaDeConclusao.await();
        long tempoFim = System.currentTimeMillis();

        // Fechando os sockets de forma limpa
        for (Socket s : conexoesAbertas) { s.close(); }

        // ==========================================
        // FASE 4: RELATÓRIO E AVALIAÇÃO DE RESULTADOS
        // ==========================================
        long tempoTotal = tempoFim - tempoInicio;

        System.out.println("\n=====================================================");
        System.out.println("               RESULTADOS DO ESTRESSE                ");
        System.out.println("=====================================================");
        System.out.println("Tempo Total de Resposta : " + tempoTotal + " ms");
        System.out.println("Vazão (Throughput)      : " + (NUMERO_PASSAGEIROS_CONCORRENTES / (tempoTotal / 1000.0)) + " req/seg");
        System.out.println("-----------------------------------------------------");
        System.out.println("Vagas Físicas Disponíveis : " + VAGAS_DISPONIVEIS);
        System.out.println("Passageiros que Tentaram  : " + NUMERO_PASSAGEIROS_CONCORRENTES);
        System.out.println("Reservas CONFIRMADAS      : " + reservasConfirmadas.get() + " (Esperado: " + VAGAS_DISPONIVEIS + ")");
        System.out.println("Reservas REJEITADAS       : " + reservasRejeitadas.get() + " (Esperado: " + (NUMERO_PASSAGEIROS_CONCORRENTES - VAGAS_DISPONIVEIS) + ")");
        System.out.println("-----------------------------------------------------");

        if (reservasConfirmadas.get() == VAGAS_DISPONIVEIS) {
            System.out.println("✅ SUCESSO ABSOLUTO! O servidor bloqueou o overbooking corretamente!");
        } else if (reservasConfirmadas.get() > VAGAS_DISPONIVEIS) {
            System.out.println("❌ FALHA CRÍTICA! Ocorreu OVERBOOKING (Condição de Corrida)!");
        } else {
            System.out.println("⚠️ ALERTA: Menos reservas confirmadas do que vagas (Pode ser erro de timeout).");
        }
    }

    // ==========================================
    // MÉTODOS UTILITÁRIOS PARA O SOCKET E PARSER
    // ==========================================
    private static String enviarRequisicaoSimples(String tipo, String dados, String token) throws Exception {
        try (Socket socket = new Socket(IP_SERVIDOR, PORTA);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println(formatarEnvelope(tipo, dados, token));
            return in.readLine();
        }
    }

    private static String formatarEnvelope(String tipo, String dados, String token) {
        return tipo + "|" + dados + "|" + token + "|" + dados.length();
    }

    private static String extrairValor(String json, String regex) {
        if (json == null) return "";
        Matcher m = Pattern.compile(regex).matcher(json);
        return m.find() ? m.group(1) : "";
    }
}