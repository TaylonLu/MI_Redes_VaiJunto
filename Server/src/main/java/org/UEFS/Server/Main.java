package org.UEFS.Server;

import org.UEFS.Server.controller.CaronaController;
import org.UEFS.Server.controller.ControllerService;
import org.UEFS.Server.controller.SessionsController;
import org.UEFS.Server.controller.UserController;
import org.UEFS.Server.http.ServerManager;
import org.UEFS.Server.http.relogio.EstadoLocal;
import org.UEFS.Server.http.relogio.ServerConstant;
import org.UEFS.Server.domain.caronas.CaronaRepo;
import org.UEFS.Server.domain.caronas.CaronaService;
import org.UEFS.Server.domain.reservas.ReservaRepo;
import org.UEFS.Server.http.HttpServer;
import org.UEFS.shared.FileManager;
import org.UEFS.Server.domain.usuarios.UserRepo;
import org.UEFS.Server.util.IOUtils;

import java.net.*;
import java.util.Collections;

public class Main {
    public static void main(String[] args) throws Exception {
        String portaVal = System.getProperty("server.port"); // vem de -Dserver.port=...
        String ipVal = ipLocal();

        System.out.println("O ip vindo de InetAddress: " + ipVal);

        if (portaVal == null || ipVal == null) {
            if (args.length < 1) {
                IOUtils.fprintln("[:red]Erro: informe a porta ou servidor corretos.[::]");
                return;
            }
            portaVal = args[args.length - 1];
        }

        if (!portaVal.matches("^\\d+$")) {
            IOUtils.fprintln("[:red]Erro: O argumento deve ser um número inteiro.[::]");
            return;
        }

        int port = Integer.parseInt(portaVal);

        ControllerService service = ControllerService.getInstance();

        service.register(FileManager.class, new FileManager());
        service.register(SessionsController.class, new SessionsController());
        UserRepo userRepo =  new UserRepo();
        service.register(UserController.class, new UserController(userRepo));
        CaronaRepo caronaRepo = new CaronaRepo();
        ReservaRepo reservaRepo = new ReservaRepo();
        service.register(CaronaService.class, new CaronaService(caronaRepo, reservaRepo));
        service.register(CaronaController.class, new CaronaController(caronaRepo));

        service.register(EstadoLocal.class, new EstadoLocal());
        service.register(ServerManager.class, new ServerManager());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            IOUtils.fprintln("\n[:yellow]Desligamento detectado. Salvando dados...[::]");

            userRepo.salvarNoDisco();
            caronaRepo.salvarNoDisco();
            reservaRepo.salvarNoDisco();

            IOUtils.fprintln("[:green]Dados salvos no disco.[::]");
        }));

//        MainServer server = new MainServer(port);
//
//        server.start();

        EstadoLocal estado = new EstadoLocal();
        ServerConstant.setMeuId(ipVal + ":" + portaVal.trim());
        service.register(EstadoLocal.class, estado);

        HttpServer server = new HttpServer(port);
        server.Start();
    }

    public static String getServerIP() {
        try {
            return Inet4Address.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            throw new RuntimeException("Não foi possível determinar o IP do servidor.", e);
        }
    }

    static String ipLocal() throws SocketException {
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
            for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                if (addr instanceof Inet4Address) {
                    return addr.getHostAddress();
                }
            }
        }
        return "localhost";
    }
}
