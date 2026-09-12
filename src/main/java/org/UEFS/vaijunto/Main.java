package org.UEFS.vaijunto;

import org.UEFS.vaijunto.Controller.CaronaController;
import org.UEFS.vaijunto.Controller.ControllerService;
import org.UEFS.vaijunto.Controller.SessionsController;
import org.UEFS.vaijunto.Controller.UserController;
import org.UEFS.vaijunto.Domain.Caronas.CaronaRepo;
import org.UEFS.vaijunto.Domain.Caronas.CaronaService;
import org.UEFS.vaijunto.Domain.FileManager;
import org.UEFS.vaijunto.Domain.Usuarios.UserRepo;
import org.UEFS.vaijunto.Server.MainServer;
import org.UEFS.vaijunto.Util.IOUtils;

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
}
