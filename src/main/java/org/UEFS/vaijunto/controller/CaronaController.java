package org.UEFS.vaijunto.controller;

import org.UEFS.shared.JsonUtils;
import org.UEFS.shared.dto.*;
import org.UEFS.shared.dto.requests.BuscaCaronaRequest;
import org.UEFS.shared.dto.requests.NovaCaronaRequest;
import org.UEFS.shared.dto.requests.ReservaRequest;
import org.UEFS.shared.dto.responses.CaronaResponse;
import org.UEFS.shared.dto.responses.PassageiroPorTrechoResponse;
import org.UEFS.shared.enums.StatusCarona;
import org.UEFS.vaijunto.domain.caronas.*;
import org.UEFS.vaijunto.domain.usuarios.Usuario;
import org.UEFS.vaijunto.exceptions.ForbiddenException;
import org.UEFS.vaijunto.exceptions.IncorrectDataException;
import org.UEFS.vaijunto.exceptions.IncorrectRequestException;
import org.UEFS.shared.enums.Status;
import org.UEFS.vaijunto.server.Request;
import org.UEFS.vaijunto.server.Response;

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

            NovaCaronaRequest caronaDTO = JsonUtils.fromJson(request.getDados(), NovaCaronaRequest.class);

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

            List<CaronaResponse> caronas = repo.getDadosList().stream()
                    .filter(C -> C.getId_motorista().equals(userID))
                    .map(this::toCaronaResponseDTO)
                    .toList();
            return new Response(
                    Status.SUCESSO.getCodigo(), "CARONAS_MOTORISTA",
                    JsonUtils.toJson(caronas)
                    );
        } catch (ForbiddenException e) {
            return e.toResponse();
        }
    }

    public Response consultarPassageiros(Request request) {
        try {
            Carona C = encontrarCaronaValidado(request.getToken(), request.getDados());

            List<PassageiroPorTrechoResponse> passageiros = C.getOcupacaoPorTrecho().entrySet().stream()
                    .map(E -> new PassageiroPorTrechoResponse(E.getKey(), getNomesPassageiros(E.getValue())))
                    .toList();

            return new Response(Status.SUCESSO.getCodigo(), "DADOS_PASSAGEIROS", JsonUtils.toJson(passageiros));
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

            BuscaCaronaRequest buscarDTO = JsonUtils.fromJson(request.getDados(), BuscaCaronaRequest.class);

            List<List<ArestaTrecho>> caminhosBrutos = caronaService.buscarItinerarios(buscarDTO);

            List<ItinerarioDTO> itinerarioDTO = caronaService.toItinerariosDTO(caminhosBrutos);

            String jsonResposta = JsonUtils.toJson(itinerarioDTO);
            return new Response(Status.SUCESSO.getCodigo(), "ITINERARIOS_BUSCADOS", jsonResposta);
        } catch (ForbiddenException | IncorrectRequestException e) {
            return e.toResponse();
        }
    }

    public Response consultarMinhasReservas(Request request) {
        try {
            String userID = userController.vaidadeUsuarioLogado(request.getToken());

            List<CaronaResponse> minhasReservas = repo.getDadosList().stream()
                    .filter(carona -> carona.getOcupacaoPorTrecho().values().stream()
                            .anyMatch(passageiros -> passageiros.contains(userID)))
                    .map(this::toCaronaResponseDTO)
                    .toList();

            return new Response(
                    Status.SUCESSO.getCodigo(), "RESERVAS_PASSAGEIRO",
                    JsonUtils.toJson(minhasReservas)
            );

        } catch (ForbiddenException e) {
            return e.toResponse();
        }
    }

    public Response confirmarReserva(Request request) {
        try {
            String userID = userController.vaidadeUsuarioLogado(request.getToken());

            ReservaRequest reservaDTO = JsonUtils.fromJson(request.getDados(), ReservaRequest.class);

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

    private CaronaResponse toCaronaResponseDTO(Carona carona) {
        return new CaronaResponse(
                carona.getId(), carona.getId_motorista(),
                carona.getVagasTotais(), carona.getData(),
                carona.getStatus().toString(),
                carona.toTrechoOcupacao()
        );
    }
}
