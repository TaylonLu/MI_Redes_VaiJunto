package org.UEFS.Server;

import org.UEFS.Server.controller.CaronaController;
import org.UEFS.Server.controller.ControllerService;
import org.UEFS.Server.controller.SessionsController;
import org.UEFS.Server.controller.UserController;
import org.UEFS.Server.controller.relogio.EstadoLocal;
import org.UEFS.Server.controller.relogio.ServerConstant;
import org.UEFS.Server.domain.caronas.CaronaRepo;
import org.UEFS.Server.domain.caronas.CaronaService;
import org.UEFS.Server.domain.reservas.ReservaRepo;
import org.UEFS.Server.http.HttpServer;
import org.UEFS.Server.server.MenuServidores;
import org.UEFS.shared.FileManager;
import org.UEFS.Server.domain.usuarios.UserRepo;
import org.UEFS.Server.server.MainServer;
import org.UEFS.Server.util.IOUtils;

import java.net.Inet4Address;
import java.net.UnknownHostException;

public class Main {
    public static void main(String[] args) throws Exception {
        String portaVal = System.getProperty("server.port"); // vem de -Dserver.port=...

        if (portaVal == null) {
            if (args.length < 1) {
                IOUtils.fprintln("[:red]Erro: informe a porta (-Dserver.port=NNNN ou como argumento).[::]");
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

        ServerConstant.setMeuId("172.20.167.4:2602"); // ou pela opção 1
        EstadoLocal estado = new EstadoLocal();
        service.register(EstadoLocal.class, estado);
        new Thread(new MenuServidores(estado)::executar, "menu").start();

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
}
