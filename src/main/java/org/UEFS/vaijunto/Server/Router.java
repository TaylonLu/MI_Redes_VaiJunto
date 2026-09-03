package org.UEFS.vaijunto.Server;

import org.UEFS.vaijunto.Controller.ControllerService;
import org.UEFS.vaijunto.Controller.UserController;
import org.UEFS.vaijunto.Exceptions.Status;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class Router {
    private final Map<String, Function<Request, Response>> routes = new HashMap<>();

    public Router() {
        UserController userController = ControllerService.getInstance().get(UserController.class);

        routes.put("LOGIN", userController::login);
        routes.put("CADASTRO", userController::cadastrar);
        routes.put("CADASTRO_MOTORISTA", userController::cadastrarMotorista);
    }

    public Response processar(Request request) {
        String metodo = request.getTipo().toUpperCase();

        Function<Request, Response> acao = routes.get(metodo);

        return acao != null
                ? acao.apply(request)
                : new Response(Status.NAO_IMPLEMENTADO);
    }
}
