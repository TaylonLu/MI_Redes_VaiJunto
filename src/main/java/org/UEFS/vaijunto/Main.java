package org.UEFS.vaijunto;

import org.UEFS.vaijunto.Controller.ControllerService;
import org.UEFS.vaijunto.Controller.SessionsController;
import org.UEFS.vaijunto.Controller.UserController;
import org.UEFS.vaijunto.Domain.FileManager;
import org.UEFS.vaijunto.Domain.Usuarios.UserMapper;
import org.UEFS.vaijunto.Domain.Usuarios.UserRepo;
import org.UEFS.vaijunto.Util.IOUtils;
import org.UEFS.vaijunto.Server.MainServer;

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
        UserRepo userRepo =  new UserRepo(new UserMapper());
        service.register(UserController.class, new UserController(userRepo));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            IOUtils.fprintln("\n[:yellow]Desligamento detectado. Salvando dados...[::]");
            int succ = service.get(FileManager.class).salvar(userRepo.copy(), "usuarios.txt", new UserMapper());
            switch (succ) {
                case 0 -> IOUtils.fprintln("[:green]Dados salvos com sucesso. Servidor encerrado.[::]");
                case -2 -> IOUtils.fprintln("[:green]Nenhum dado para salvar.[::]");
                default -> IOUtils.fprintln("[:red]Erro ao salvar dados: [::] " + succ);
            }
        }));

        MainServer server = new MainServer(port);

        server.start();

    }
}
