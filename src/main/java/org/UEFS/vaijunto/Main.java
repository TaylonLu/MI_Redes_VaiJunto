package org.UEFS.vaijunto;

import org.UEFS.vaijunto.controller.CaronaController;
import org.UEFS.vaijunto.controller.ControllerService;
import org.UEFS.vaijunto.controller.SessionsController;
import org.UEFS.vaijunto.controller.UserController;
import org.UEFS.vaijunto.domain.caronas.CaronaRepo;
import org.UEFS.vaijunto.domain.caronas.CaronaService;
import org.UEFS.vaijunto.util.FileManager;
import org.UEFS.vaijunto.domain.usuarios.UserRepo;
import org.UEFS.vaijunto.server.MainServer;
import org.UEFS.vaijunto.util.IOUtils;

import java.net.Inet4Address;
import java.net.UnknownHostException;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            IOUtils.fprintln("[:red]Erro: Número de argumentos inválido. Uso: java MainServer <porta>[::]");
            return;
        }

        if (!args[0].matches("^\\d+$")) { // Removido o suporte a negativos
            IOUtils.fprintln("[:red]Erro: O argumento deve ser um número inteiro.[::]");
            return;
        }

        int port = Integer.parseInt(args[0]);
        if (port <= 1024 || port > 65535) {
            IOUtils.fprintln("[:red]Erro: A porta deve estar entre 1025 e 65535.[::]");
            return;
        }

        ControllerService service = ControllerService.getInstance();

        service.register(FileManager.class, new FileManager());
        service.register(SessionsController.class, new SessionsController());
        UserRepo userRepo =  new UserRepo();
        service.register(UserController.class, new UserController(userRepo));
        CaronaRepo caronaRepo = new CaronaRepo();
        service.register(CaronaService.class, new CaronaService(caronaRepo));
        service.register(CaronaController.class, new CaronaController(caronaRepo));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            IOUtils.fprintln("\n[:yellow]Desligamento detectado. Salvando dados...[::]");
            userRepo.salvarNoDisco();
            caronaRepo.salvarNoDisco();
            IOUtils.fprintln("[:green]Dados salvos no disco.[::]");
        }));

        MainServer server = new MainServer(port);

        server.start();

    }

    public static String getServerIP() {
        try {
            return Inet4Address.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            // É melhor lançar um erro claro do que apenas um RuntimeException genérico
            throw new RuntimeException("Não foi possível determinar o IP do servidor.", e);
        }
    }
}
