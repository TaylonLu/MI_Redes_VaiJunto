package org.UEFS.vaijunto.Controller;

import org.UEFS.vaijunto.DTO.*;
import org.UEFS.vaijunto.Domain.Caronas.*;
import org.UEFS.vaijunto.Domain.Usuarios.Usuario;
import org.UEFS.vaijunto.Exceptions.ForbiddenException;
import org.UEFS.vaijunto.Exceptions.IncorrectDataException;
import org.UEFS.vaijunto.Exceptions.IncorrectRequestException;
import org.UEFS.vaijunto.Exceptions.Status;
import org.UEFS.vaijunto.Server.Request;
import org.UEFS.vaijunto.Server.Response;
import org.UEFS.vaijunto.Server.ServerGrammar;

import java.util.List;
import java.util.Set;

public class CaronaController {
    protected final ControllerService serviceProvider = ControllerService.getInstance();
    private final CaronaService caronaService = serviceProvider.get(CaronaService.class);

    private final CaronaRepo repo;
    private final UserController userController;

    public CaronaController(CaronaRepo repo) {
        this.repo = repo;
        this.userController = serviceProvider.get(UserController.class);
    }

    public Response criarCarona(Request request) {
        try {
            String userID = validarMotorista(request.getToken());

            NovaCaronaRequest caronaDTO = ServerGrammar.fromJson(request.getDados(), NovaCaronaRequest.class);

            if (!userID.equals(caronaDTO.idMotorista())) return new IncorrectRequestException("Token Inválido").toResponse();

            Carona C = new Carona(
                    new Rota(caronaDTO.trechos()),
                    caronaDTO.idMotorista(),
                    caronaDTO.capacidade(),
                    caronaDTO.data()
            );

            repo.salvar(C);

            return new Response(Status.SUCESSO);

        } catch (IncorrectDataException | IncorrectRequestException | ForbiddenException e) {
            return e.toResponse();
        }
    }

    public Response consultarCaronas(Request request) {
        try {
            String userID = validarMotorista(request.getToken());

            List<CaronaResponseDTO> caronas = repo.getDadosList().stream()
                    .filter(C -> C.getId_motorista().equals(userID))
                    .map(this::toCaronaResponseDTO)
                    .toList();
            return new Response(
                    Status.SUCESSO.getCodigo(), "CARONAS_MOTORISTA",
                    ServerGrammar.toJson(caronas)
                    );
        } catch (ForbiddenException e) {
            return e.toResponse();
        }
    }

    public Response consultarPassageiros(Request request) {
        try {
            Carona C = encontrarCaronaValidado(request.getToken(), request.getDados());

            List<PassageiroPorTrechoResponseDTO> passageiros = C.getOcupacaoPorTrecho().entrySet().stream()
                    .map(E -> new PassageiroPorTrechoResponseDTO(E.getKey(), getNomesPassageiros(E.getValue())))
                    .toList();

            return new Response(Status.SUCESSO.getCodigo(), "DADOS_PASSAGEIROS", ServerGrammar.toJson(passageiros));
        } catch (ForbiddenException e) {
            return e.toResponse();
        }
    }

    public Response cancelarCarona(Request request) {
        try {
            Carona C = encontrarCaronaValidado(request.getToken(), request.getDados());

            C.setStatus(StatusCarona.CANCELADA);

            return new Response(Status.SUCESSO);
        } catch (ForbiddenException e) {
            return e.toResponse();
        }
    }

    public Response buscarItinerarios(Request request) {
        try {
            userController.vaidadeUsuarioLogado(request.getToken());

            BuscaCaronaDTO buscarDTO = ServerGrammar.fromJson(request.getDados(), BuscaCaronaDTO.class);

            List<List<ArestaTrecho>> caminhosBrutos = caronaService.buscarItinerarios(buscarDTO);

            List<ItinerarioDTO> itinerarioDTO = caronaService.toItinerariosDTO(caminhosBrutos);

            String jsonResposta = ServerGrammar.toJson(itinerarioDTO);
            return new Response(Status.SUCESSO.getCodigo(), "ITINERARIOS_BUSCADOS", jsonResposta);
        } catch (ForbiddenException | IncorrectRequestException e) {
            return e.toResponse();
        }
    }

    public Response consultarMinhasReservas(Request request) {
        try {
            String userID = userController.vaidadeUsuarioLogado(request.getToken());

            List<CaronaResponseDTO> minhasReservas = repo.getDadosList().stream()
                    .filter(carona -> carona.getOcupacaoPorTrecho().values().stream()
                            .anyMatch(passageiros -> passageiros.contains(userID)))
                    .map(this::toCaronaResponseDTO)
                    .toList();

            return new Response(
                    Status.SUCESSO.getCodigo(), "RESERVAS_PASSAGEIRO",
                    ServerGrammar.toJson(minhasReservas)
            );

        } catch (ForbiddenException e) {
            return e.toResponse();
        }
    }

    public Response confirmarReserva(Request request) {
        try {
            String userID = userController.vaidadeUsuarioLogado(request.getToken());

            ReservaRequest reservaDTO = ServerGrammar.fromJson(request.getDados(), ReservaRequest.class);

            List<ArestaTrecho> itinerarioEscolhido = reservaDTO.passos().stream()
                    .map(passo -> {
                        Carona carona = repo.getByID(passo.idCarona());
                        if (carona == null) throw new IncorrectRequestException("Carona não encontrada.");
                        return new ArestaTrecho(passo.trecho().fim(), carona, passo.trecho());
                    })
                    .toList();

            boolean sucesso = caronaService.confirmarReservaAtomica(userID, itinerarioEscolhido);

            if (sucesso) {
                return new Response(Status.SUCESSO.getCodigo(), "RESERVA_CONFIRMADA", "");
            } else {
                return new Response(Status.CONFLITO.getCodigo(), "FALHA_RESERVA", "Vagas esgotadas.");
            }

        } catch (ForbiddenException | IncorrectRequestException e) {
            return e.toResponse();
        }
    }

    private String validarMotorista(String token) throws ForbiddenException {
        String userID = userController.vaidadeUsuarioLogado(token);

        Usuario user = userController.getByID(userID);
        if (user == null || !user.isMotorista()) throw new ForbiddenException("Usuário não é um motorista.");

        return userID;
    }

    private Carona encontrarCaronaValidado(String token, String idCarona) throws ForbiddenException {
        String userID = validarMotorista(token);

        Carona C = repo.getByID(idCarona);

        if (C == null || !C.getId_motorista().equals(userID))
            throw new ForbiddenException("Carona não existe ou usuário não term permissão para a consultar.");

        return C;
    }

    private List<String> getNomesPassageiros(Set<String> value) {
        return value.stream()
                .map(userController::getByID)
                .map(Usuario::getNome)
                .toList();
    }

    private CaronaResponseDTO toCaronaResponseDTO(Carona carona) {
        return new CaronaResponseDTO(
                carona.getId(), carona.getId_motorista(),
                carona.getVagasTotais(), carona.getData(),
                carona.getStatus().toString(),
                carona.toTrechoOcupacao()
        );
    }
}
