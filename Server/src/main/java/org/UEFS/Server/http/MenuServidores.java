package org.UEFS.Server.http;

import org.UEFS.Server.http.relogio.ServerConstant;
import org.UEFS.Server.http.service.Vizinho;
import org.UEFS.Server.util.IOUtils;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.UEFS.Server.util.IOUtils.fprintf;
import static org.UEFS.Server.util.IOUtils.fprintln;

public class MenuServidores {
    private static final int TIMEOUT_TEST_MS = 2_000;
    private static final String ERRO_ID = "[:red]:: ID inválido. Use [:yellow]host:porta[:red] (porta de 1 a 65535). [::]";

    public MenuServidores() {
        // Construtor vazio limpo (sem injeção de EstadoLocal)
    }

    public void executar() {
        fprintln("[:purple]Rodando Menu do Consórcio...[::]");
        boolean rodando = true;

        while (rodando) {
            imprimirMenu();
            String OP;

            try {
                OP = IOUtils.ask("[:cyan]> [::]");
            } catch (RuntimeException e) {
                break;
            }

            switch (OP) {
                case "1" -> definirMeuId();
                case "2" -> adicionarVizinho();
                case "3" -> listarVizinhos();
                case "4" -> testarConexoes();
                case "5" -> status();
                case "9" -> IOUtils.limparTela();
                case "0" -> rodando = false;
                default -> fprintln("[:red]:: Opção inválida.[::]");
            }
        }
        fprintln("[:yellow]| Menu encerrado (o servidor continua rodando em background).[::]");
    }

    private void definirMeuId() {
        final String SEM_ID = "não definido";
        String atual = ServerConstant.getMeuId();

        String ip = IOUtils.askOrKeep("Meu ID (ex.: localhost:3001)",
                atual == null ? SEM_ID : atual,
                MenuServidores::idValido, ERRO_ID);

        if (ip == null || ip.equals(SEM_ID) || ip.equals(atual)) {
            fprintln("[:yellow]| Nada foi alterado[::]");
            return;
        }
        Vizinho novo = new Vizinho(ip);

        if (ServerConstant.getVizinhos().contains(novo)) {
            fprintln("[:red]:: Esse ID já está na lista de vizinhos. Remova-o primeiro.[::]");
            return;
        }

        ServerConstant.setMeuId(ip);
        fprintf("[:green]| Meu ID foi definido: %s[::]%n", novo.id());
    }

    private void adicionarVizinho() {
        String id = IOUtils.askWhile("Vizinho de fronteira a adicionar (ip:porta)", MenuServidores::idValido, ERRO_ID);
        if (id == null) return; // cancelou

        if (id.equals(ServerConstant.getMeuId())) {
            fprintln("[:red]:: Esse é o seu próprio ID.[::]");
            return;
        }

        Vizinho novo = new Vizinho(id);

        if (ServerConstant.getVizinhos().contains(novo)) {
            fprintln("[:yellow]| Vizinho já cadastrado.[::]");
            return;
        }

        // Correção: Efetivamente adiciona o vizinho à memória do servidor
        ServerConstant.getVizinhos().add(novo);
        fprintf("[:green]| Vizinho adicionado: %s[::]%n", id);

        if (alcancavel(id)) fprintln("[:green]  -> porta respondendo.[::]");
        else fprintln("[:yellow]  -> aviso: não foi possível conectar agora (servidor desligado ou firewall).[::]");
    }

    // ------------------------------------------------------- consulta

    private void listarVizinhos() {
        String meu = ServerConstant.getMeuId();
        fprintf("[:cyan]Meu ID:[::] %s%n", meu == null ? "(não definido)" : meu);

        if (ServerConstant.getVizinhos().isEmpty()) {
            fprintln("[:yellow]| Nenhum vizinho de rede cadastrado.[::]");
            return;
        }

        fprintln("[:cyan]Vizinhos (Fronteiras P2P):[::]");
        for (Vizinho V : ServerConstant.getVizinhos())
            fprintf("  - %s%n", V.id());
    }

    private void testarConexoes() {
        List<String> lista = ServerConstant.getVizinhos().stream().map(Vizinho::id).toList();
        if (lista.isEmpty()) {
            fprintln("[:yellow]| Nenhum vizinho cadastrado.[::]");
            return;
        }

        fprintf("[:yellow]| Testando (timeout de %ds por servidor)...[::]%n", TIMEOUT_TEST_MS / 1000);

        List<CompletableFuture<Boolean>> testes = new ArrayList<>();
        for (String id : lista)
            testes.add(CompletableFuture.supplyAsync(() -> alcancavel(id)));

        for (int i = 0; i < lista.size(); i++) {
            boolean ok = testes.get(i).join();
            fprintf("  %-28s %s%n", lista.get(i), ok ? "[:green]ONLINE[::]" : "[:red]OFFLINE[::]");
        }
    }

    private void status() {
        String meu = ServerConstant.getMeuId();

        fprintf("[:cyan]Meu ID:[::]       %s%n", meu == null ? "(não definido)" : meu);
        fprintf("[:cyan]Vizinhos:[::]     %d%n", ServerConstant.getVizinhos().size());
        fprintln("[:cyan]Modo de Rede:[::]  Descentralizado (Consórcio P2P)");
    }

    // === MENU === //
    private void imprimirMenu() {
        fprintln("");
        fprintln("[:cyan][:bold]========== CONSÓRCIO: GERÊNCIA DE REDE ==========[::]");
        fprintln(" [:yellow]1[::]) Definir meu ID (ip:porta)");
        fprintln(" [:yellow]2[::]) Adicionar vizinho (fronteira)");
        fprintln(" [:yellow]3[::]) Listar vizinhos");
        fprintln(" [:yellow]4[::]) Testar conexão com os vizinhos");
        fprintln(" [:yellow]5[::]) Status do nó local");
        fprintln(" [:yellow]9[::]) Limpar tela");
        fprintln(" [:yellow]0[::]) Sair do menu");
        fprintln("[:cyan][:bold]=================================================[::]");
    }

    // ------------------------------------------------------- utilitários

    private static boolean idValido(String id) {
        int I = id.lastIndexOf(':');

        if (I <= 0 || I == id.length() - 1) return false;

        try {
            int porta = Integer.parseInt(id.substring(I + 1));
            return porta > 0 && porta <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean alcancavel(String id) {
        int i = id.lastIndexOf(':');
        String host = id.substring(0, i);
        int porta = Integer.parseInt(id.substring(i + 1));

        try (Socket S = new Socket()) {
            S.connect(new InetSocketAddress(host, porta), TIMEOUT_TEST_MS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}