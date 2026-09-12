package org.UEFS.vaijunto.Server;

import org.UEFS.vaijunto.Controller.CaronaController;
import org.UEFS.vaijunto.Controller.ControllerService;
import org.UEFS.vaijunto.Controller.UserController;
import org.UEFS.vaijunto.Exceptions.Status;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class Router {
    private final Map<String, Function<Request, Response>> routes = new HashMap<>();

    public Router() {
        ControllerService serviceProvider = ControllerService.getInstance();

        UserController userController = serviceProvider.get(UserController.class);
        CaronaController caronaController = serviceProvider.get(CaronaController.class);

        // --- Rotas de Sessão e Usuário ---
        routes.put("LOGIN", userController::login);
        routes.put("CADASTRO", userController::cadastrar);
        routes.put("CADASTRO_MOTORISTA", userController::cadastrarMotorista);

        // --- Rotas do Motorista ---
        routes.put("CRIAR_CARONA", caronaController::criarCarona);
        routes.put("MINHAS_CARONAS", caronaController::consultarCaronas);
        routes.put("PASSAGEIROS_CARONA", caronaController::consultarPassageiros);
        routes.put("CANCELAR_CARONA", caronaController::cancelarCarona);

        // --- Rotas do Passageiro ---
        routes.put("BUSCAR_ITINERARIOS", caronaController::buscarItinerarios);
        routes.put("MINHAS_RESERVAS", caronaController::consultarMinhasReservas);

        // (Espaço reservado para a futura rota de confirmar reserva atômica)
        // routes.put("CONFIRMAR_RESERVA", caronaController::confirmarReserva);
    }

    public Response processar(Request request) {
        String metodo = request.getTipo().toUpperCase();

        Function<Request, Response> acao = routes.get(metodo);

        return acao != null
                ? acao.apply(request)
                : new Response(Status.NAO_IMPLEMENTADO, "");
    }
}