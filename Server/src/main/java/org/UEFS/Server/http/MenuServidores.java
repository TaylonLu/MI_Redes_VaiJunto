package org.UEFS.Server.http;

import org.UEFS.Server.controller.ControllerService;
import org.UEFS.Server.http.relogio.EstadoLocal;
import org.UEFS.Server.http.relogio.ServerConstant;
import org.UEFS.Server.util.IOUtils;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.UEFS.Server.util.IOUtils.fprintf;
import static org.UEFS.Server.util.IOUtils.fprintln;

public class MenuServidores {
    private static final int TIMEOUT_TEST_MS = 2_000;
    private static final String ERRO_ID = "[:red]:: ID inválido. Use [:yellow]host:porta[:red] (porta de 1 a 65535). [::]";

    private final EstadoLocal estado;

    private static final ExecutorService BG = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "menu-regiao-critica");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean solicitando = new AtomicBoolean(false);
    private final AtomicBoolean naRegiao = new AtomicBoolean(false);

    public MenuServidores() {
        this.estado = ControllerService.getInstance().get(EstadoLocal.class);
    }

    public MenuServidores(EstadoLocal estado) {
        this.estado = estado;
    }

    public void executar() {
        fprintln("[:purple]Rodando...[::]");
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
                case "2" -> adicionarServidor();
                case "3" -> removerServidor();
                case "4" -> listarServidores();
                case "5" -> testarConexoes();
                case "6" -> solicitarRegiao();
                case "7" -> liberarRegiao();
                case "8" -> status();
                case "9" -> IOUtils.limparTela();
                case "0" -> rodando = false;
                default -> fprintln("[:red]:: Opção inválida.[::]");
            }
        }
        BG.shutdownNow();
        fprintln("[:yellow]| Menu encerrado (o servidor continua rodando).[::]");
    }

    private void definirMeuId() {
        if (ocupado()) return;

        final String SEM_ID = "não definido";
        String atual = ServerConstant.getMEU_ID();

        String novo = IOUtils.askOrKeep("Meu ID (ex.: 192.168.0.10:7000)",
                atual == null ? SEM_ID : atual,
                MenuServidores::idValido, ERRO_ID);

        if (novo == null || novo.equals(SEM_ID) || novo.equals(atual)) {
            fprintln("[:yellow]| Nada foi alterado[::]");
            return;
        }

        if (ServerConstant.getServidores().contains(novo)) {
            fprintln("[:red]:: Esse ID já está na lista de outros servidores.Remova-o primeiro.[::]");
            return;
        }

        ServerConstant.setMeuId(novo);
        atualizarN();
        fprintf("[:green]| Meu ID foi definido: %s[::]", novo);
    }

    private void adicionarServidor() {
        if (ocupado()) return;

        String id = IOUtils.askWhile("Servidor a adicionar (ip:porta)", MenuServidores::idValido, ERRO_ID);
        if (id == null) return; // cancelou

        if (id.equals(ServerConstant.getMEU_ID())) {
            fprintln("[:red]:: Esse é o seu próprio ID.[::]");
            return;
        }
        if (ServerConstant.getServidores().contains(id)) {
            fprintln("[:yellow]| Servidor já cadastrado.[::]");
            return;
        }

        ServerConstant.addServidor(id);
        atualizarN();
        fprintf("[:green]| Adicionado: %s[::]%n", id);

        if (alcancavel(id)) fprintln("[:green]  -> porta respondendo.[::]");
        else fprintln("[:yellow]  -> aviso: não foi possível conectar agora (servidor desligado ou firewall).[::]");
    }

    private void removerServidor() {
        if (ocupado()) return;

        List<String> lista = new ArrayList<>(ServerConstant.getServidores());
        if (lista.isEmpty()) {
            fprintln("[:yellow]| Nenhum servidor cadastrado.[::]");
            return;
        }

        for (int i = 0; i < lista.size(); i++)
            fprintf(" [:yellow]%d[::]) %s%n", i + 1, lista.get(i));

        Integer escolha = IOUtils.askForInt("Número do servidor a remover: ",
                n -> n >= 1 && n <= lista.size(),
                "[:red]:: Número fora da lista.[::]");
        if (escolha == null) return; // cancelou

        String alvo = lista.get(escolha - 1);
        if (!IOUtils.askForConfirmation("Remover " + alvo + "?")) {
            fprintln("[:yellow]| Remoção cancelada.[::]");
            return;
        }

        ServerConstant.getServidores().remove(alvo);
        atualizarN();
        fprintf("[:green]| Removido: %s[::]%n", alvo);
    }

    // ------------------------------------------------------- consulta

    private void listarServidores() {
        String meu = ServerConstant.getMEU_ID();
        fprintf("[:cyan]Meu ID:[::] %s%n", meu == null ? "(não definido)" : meu);

        if (ServerConstant.getServidores().isEmpty()) {
            fprintln("[:yellow]| Nenhum outro servidor cadastrado.[::]");
            return;
        }

        fprintln("[:cyan]Outros servidores:[::]");
        for (String id : ServerConstant.getServidores())
            fprintf("  - %s%n", id);
    }

    private void testarConexoes() {
        List<String> lista = new ArrayList<>(ServerConstant.getServidores());
        if (lista.isEmpty()) {
            fprintln("[:yellow]| Nenhum servidor cadastrado.[::]");
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
        String meu = ServerConstant.getMEU_ID();

        fprintf("[:cyan]Meu ID:[::]               %s%n", meu == null ? "(não definido)" : meu);
        fprintf("[:cyan]Outros servidores:[::]    %d%n", ServerConstant.getServidores().size());
        fprintf("[:cyan]N:[::]                    %d%n", ServerConstant.getN());
        fprintf("[:cyan]Aguardando respostas:[::] %s%n", solicitando.get() ? "[:yellow]sim[::]" : "não");
        fprintf("[:cyan]Na região crítica:    %s%n", naRegiao.get() ? "[:green]sim[::]" : "não[::]");
    }

    // ------------------------------------------------- região crítica7

    private void solicitarRegiao() {
        if (ServerConstant.getMEU_ID() == null) {
            fprintln("[:red]:: Defina o seu ID primeiro (opção 1).[::]");
            return;
        }
        if (naRegiao.get()) {
            fprintln("[:yellow]| Você já está na região crítica. Libere-a antes (opção 7).[::]");
            return;
        }
        if (!solicitando.compareAndSet(false, true)) {
            fprintln("[:yellow]| Já existe uma solicitação em andamento.[::]");
            return;
        }

        int outros = ServerConstant.getServidores().size();
        if (outros == 0)
            fprintln("[:yellow]| Aviso: sem outros servidores cadastrados, a entrada será imediata.[::]");

        fprintf("[:yellow]| Solicitando região crítica a %d servidor(es)... (o menu continua disponível)[::]%n", outros);
        BG.submit(() -> {
            System.out.println("Solicitando...");
            try {
                estado.solicitarRegiaoCritica();
                naRegiao.set(true);
                fprintln("\n[:green][:bold][OK] Você está DENTRO da região crítica. Use a opção 7 para liberar.[::]");
            } catch (Exception e) {
                fprintln("\n[:red][:bold][FALHA][::][:red] Não entrou na região crítica: " + e.getMessage() + "[::]");
            } finally {
                solicitando.set(false);
            }
        });
    }

    private void liberarRegiao() {
        if (!naRegiao.get()) {
            fprintln("[:yellow]| Você não está na região crítica.[::]");
            return;
        }

        estado.liberarRegiaoCritica();
        naRegiao.set(false);
        fprintln("[:green]| Região crítica liberada; respostas adiadas foram enviadas.[::]");
    }


    // === MENU === //
    private void imprimirMenu() {
        fprintln("");
        fprintln("[:cyan][:bold]========== COMUNICAÇÃO ENTRE SERVIDORES ==========[::]");
        fprintln(" [:yellow]1[::]) Definir meu ID (ip:porta)");
        fprintln(" [:yellow]2[::]) Adicionar servidor");
        fprintln(" [:yellow]3[::]) Remover servidor");
        fprintln(" [:yellow]4[::]) Listar servidores");
        fprintln(" [:yellow]5[::]) Testar conexão com os servidores");
        fprintln(" [:yellow]6[::]) Solicitar região crítica");
        fprintln(" [:yellow]7[::]) Liberar região crítica");
        fprintln(" [:yellow]8[::]) Status");
        fprintln(" [:yellow]9[::]) Limpar tela");
        fprintln(" [:yellow]0[::]) Sair do menu");
        fprintln("[:cyan][:bold]==================================================[::]");
    }


    // ------------------------------------------------------- utilitários

    private boolean ocupado() {
        if (solicitando.get() || naRegiao.get()) {
            fprintln("[:red]:: Operação bloqueada: há uma solicitação em andamento ou você está na região crítica.[::]");
            return true;
        }

        return false;
    }

    /** N = total de servidores, contando este. */
    private void atualizarN() {
        ServerConstant.setN(ServerConstant.getServidores().size() + 1);
    }

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
        int porta = Integer.parseInt(id.substring(i +1));

        try (Socket S = new Socket()) {
            S.connect(new InetSocketAddress(host, porta), TIMEOUT_TEST_MS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isOcupadoSilencioso() {
        return solicitando.get() || naRegiao.get();
    }
}
