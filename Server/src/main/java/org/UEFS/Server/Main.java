package org.UEFS.Server;

import org.UEFS.Server.controller.CaronaController;
import org.UEFS.Server.controller.ControllerService;
import org.UEFS.Server.controller.SessionsController;
import org.UEFS.Server.controller.UserController;
import org.UEFS.Server.domain.caronas.CaronaRepo;
import org.UEFS.Server.domain.caronas.CaronaService;
import org.UEFS.Server.domain.cidades.CidadeMapper;
import org.UEFS.Server.domain.reservas.ReservaRepo;
import org.UEFS.Server.domain.reservas.TransacaoRepo;
import org.UEFS.Server.domain.reservas.TransacaoService;
import org.UEFS.Server.domain.usuarios.UserRepo;
import org.UEFS.Server.http.HttpServer;
import org.UEFS.Server.http.cluster.PeerClient;
import org.UEFS.Server.http.relogio.RelogioLamport;
import org.UEFS.Server.http.relogio.ServerConstant;
import org.UEFS.Server.http.service.*;
import org.UEFS.Server.util.IOUtils;
import org.UEFS.shared.FileManager;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

public class Main {
    public static void main(String[] args) throws Exception {
        String regiao = System.getProperty("server.regiao");
        if (regiao == null && args.length > 0) regiao = args[0];
        if (regiao == null || regiao.isBlank()) {
            IOUtils.fprintln("[:red]Erro: informe a região (ex.: high_rock) como argumento ou em -Dserver.regiao.[::]");
            return;
        }

        // 1. Quem eu sou na rede (região e vizinhos). Falha cedo, antes de ler qualquer arquivo de dados.
        try {
            ServerConstant.carregarRegioes();
            ServerConstant.carregar(regiao);
        } catch (IllegalArgumentException e) {
            System.err.println("Erro: " + e.getMessage());
            return;
        }

        ControllerService service = ControllerService.getInstance();
        String meuID = ServerConstant.getMeuId();

        RelogioLamport relogio = new RelogioLamport();
        PeerClient peerClient = new PeerClient(meuID, relogio);
        service.register(PeerClient.class, peerClient);

        // 2. Infraestrutura e dados (a ordem importa: os repositórios leem o disco pelo FileManager)
        service.register(FileManager.class, new FileManager());
        CidadeMapper cidadeMapper = new CidadeMapper("/public/data/cidades.json");
        service.register(CidadeMapper.class, cidadeMapper);       // a BuscaDistribuida busca o mapper por aqui
        service.register(SessionsController.class, new SessionsController());

        UserRepo userRepo = new UserRepo();
        CaronaRepo caronaRepo = new CaronaRepo();
        CaronaService caronaService = new CaronaService(caronaRepo);
        service.register(CaronaService.class, caronaService);

        TransacaoRepo transacaoRepo = new TransacaoRepo();
        TransacaoService transacoes = new TransacaoService(meuID, transacaoRepo, caronaRepo, caronaService);
        service.register(TransacaoService.class, transacoes);
        transacoes.reaplicarOcupacao(); // garante as vagas das transações já guardadas
        RecuperadorTransacoes recuperador = new RecuperadorTransacoes(meuID, transacoes, peerClient);

        service.register(UserController.class, new UserController(userRepo));
        service.register(CaronaController.class, new CaronaController(caronaRepo, cidadeMapper));

        // 3. Rede entre servidores: relógio, cliente HTTP dos vizinhos e a busca distribuída
        BuscaDistribuida busca = new BuscaDistribuida(meuID, ServerConstant.getVizinhos(), caronaRepo, peerClient);

        ReservaParticipante participante = new ReservaParticipante(meuID, ServerConstant.getVizinhos(), transacoes, peerClient);
        ReservaCoordenador coordenador = new ReservaCoordenador(meuID, transacoes, peerClient, relogio);
        service.register(ServerManager.class, new ServerManager(busca, relogio, participante, coordenador));

        // 4. Salvar ao desligar
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            IOUtils.fprintln("\n[:yellow]Desligamento detectado. Salvando dados...[::]");

            recuperador.encerrar();

            userRepo.salvarNoDisco();
            caronaRepo.salvarNoDisco();
            transacaoRepo.salvarNoDisco();

            IOUtils.fprintln("[:green]Dados salvos no disco.[::]");
        }));

        // 5. Subir o servidor HTTP
        mostrarResumo();
        new HttpServer(ServerConstant.getPorta()).Start();

        recuperador.iniciar();
    }

    private static void mostrarResumo() throws SocketException {
        IOUtils.fprintln("[:green]Região " + ServerConstant.getMeuId() + " na porta " + ServerConstant.getPorta() + "[::]");
        for (Vizinho v : ServerConstant.getVizinhos())
            System.out.println("  vizinho " + v.id() + " -> " + v.url());
        String regiao = ServerConstant.getRegiao().regiaoBase()
                .replaceAll("[^a-zA-Z0-9]", "")
                .toUpperCase(java.util.Locale.ROOT);

        System.out.println("  Para que outros nós alcancem este servidor, configure: HOST_"
                + regiao + "=<IP_DA_MAQUINA>");

        if (Files.exists(Path.of("/.dockerenv")) || Files.exists(Path.of("/run/.containerenv"))) {
            System.out.println("  Execução em contêiner: use o IPv4 da máquina hospedeira, não o IP interno do contêiner.");
        } else {
            System.out.println("  IPv4 detectado nesta máquina: " + ipLocal()
                    + " (confirme que é acessível pelos outros nós).");
        }
    }

    static String ipLocal() throws SocketException {
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
            for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                if (addr instanceof Inet4Address) return addr.getHostAddress();
            }
        }
        return "localhost";
    }
}