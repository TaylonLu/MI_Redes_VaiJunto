package org.UEFS.vaijunto.Server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class StressTest {

    public static void main(String[] args) {
        String ipServidor = "localhost";
        int porta = 2602; // Altere para a porta que você está usando
        int numeroDeClientes = 5;

        System.out.println("🚀 Iniciando Teste de Estresse com " + numeroDeClientes + " clientes simultâneos...\n");

        for (int i = 1; i <= numeroDeClientes; i++) {
            final int idCliente = i;

            // Cria uma Thread para cada cliente, simulando acesso simultâneo
            new Thread(() -> {
                try (Socket socket = new Socket(ipServidor, porta);
                     PrintWriter enviar = new PrintWriter(socket.getOutputStream(), true);
                     BufferedReader receber = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                    // 1. O Cliente tenta se CADASTRAR
                    // Formato DVCP: TIPO|TOKEN|TAM|DADOS (Ajuste os dados conforme a sua gramática)
                    String email = "cliente" + idCliente + "@teste.com";
                    String senha = "senha" + idCliente;
                    String nome = "Cliente Teste " + idCliente;

                    String comandoCadastro = "CADASTRO|" + email + ";" + senha + ";" + nome + "|0|20|";
                    System.out.println("[Cliente " + idCliente + "] Enviando: " + comandoCadastro);
                    enviar.println(comandoCadastro);

                    String respostaCadastro = receber.readLine();
                    System.out.println("[Cliente " + idCliente + "] Recebeu: " + respostaCadastro);
//
//                    // Pequena pausa para simular o tempo de o usuário ir para a tela de login
//                    Thread.sleep(500);
//
//                    // 2. O Cliente tenta fazer LOGIN
//                    String comandoLogin = "LOGIN|" + email + ";" + senha + "|0|22";
//                    System.out.println("[Cliente " + idCliente + "] Enviando: " + comandoLogin);
//                    enviar.println(comandoLogin);
//
//                    String respostaLogin = receber.readLine();
//                    System.out.println("[Cliente " + idCliente + "] Recebeu: " + respostaLogin);

                } catch (Exception e) {
                    System.out.println("❌ [Cliente " + idCliente + "] Erro: " + e.getMessage());
                }
            }).start();
        }
    }
}