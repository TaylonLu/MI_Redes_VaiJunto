package org.UEFS.vaijunto.Server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class ClientTest {
    public static void main(String[] args) {
        String ipServidor = "localhost";
        int porta = 8080;

        try {
            Socket socket = new Socket(ipServidor, porta);
            System.out.println("✅ Conectado ao Servidor VaiJunto! (Digite 'SAIR' para fechar)");

            PrintWriter enviarParaServidor = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader receberDoServidor = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            Scanner teclado = new Scanner(System.in);

            while (true) {
                System.out.print("\nDigite seu comando (Ex: CADASTRO|Email;Senha;nome|token|tamanho): ");
                String meuComando = teclado.nextLine();

                enviarParaServidor.println(meuComando);

                if (meuComando.equalsIgnoreCase("SAIR")) {
                    System.out.println("Encerrando cliente...");
                    break;
                }

                String resposta = receberDoServidor.readLine();
                if (resposta == null) {
                    System.out.println("O servidor fechou a conexão.");
                    break;
                }

                System.out.println("O Servidor respondeu: " + resposta);
            }

            socket.close();
            teclado.close();

        } catch (Exception e) {
            System.out.println("❌ Erro na comunicação com o servidor: " + e.getMessage());
        }
    }
}