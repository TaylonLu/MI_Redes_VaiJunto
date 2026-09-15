package org.UEFS.Server.server;

import org.UEFS.shared.model.Request;
import org.UEFS.shared.model.Response;
import org.UEFS.Server.controller.CaronaController;
import org.UEFS.Server.controller.ControllerService;
import org.UEFS.Server.controller.UserController;
import org.UEFS.shared.enums.Status;
import org.UEFS.shared.model.ServerException;

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
        routes.put("CONFIRMAR_RESERVA", caronaController::confirmarReserva);
    }

    public Response processar(Request request) {
        String metodo = request.getTipo().toUpperCase();

        Function<Request, Response> acao = routes.get(metodo);

        try {
            return acao.apply(request);
        } catch (ServerException e) {
            return e.toResponse();
        } catch (Exception e) {
            return new Response(Status.BAD_REQUEST.getCodigo(), "ERRO_INTERNO", "Erro interno do servidor");
        }
    }
}