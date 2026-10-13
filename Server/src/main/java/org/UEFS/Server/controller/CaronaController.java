package org.UEFS.Server.controller;


import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import org.UEFS.Server.domain.caronas.*;
import org.UEFS.Server.domain.reservas.Reserva;
import org.UEFS.Server.domain.reservas.ReservaRepo;
import org.UEFS.Server.domain.usuarios.Usuario;
import org.UEFS.Server.exceptions.*;
import org.UEFS.shared.JsonUtils;
import org.UEFS.shared.dto.*;
import org.UEFS.shared.dto.requests.BuscaCaronaRequest;
import org.UEFS.shared.dto.requests.NovaCaronaRequest;
import org.UEFS.shared.dto.responses.CaronaResponse;
import org.UEFS.shared.dto.responses.PassageiroPorTrechoResponse;
import org.UEFS.shared.dto.responses.ReservaResponse;
import org.UEFS.shared.enums.Status;
import org.UEFS.shared.enums.StatusCarona;
import org.UEFS.shared.enums.StatusReserva;
import org.UEFS.shared.model.Request;
import org.UEFS.shared.model.Response;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.UEFS.Server.http.Utils.tokenDe;

public class CaronaController {
    protected final ControllerService serviceProvider = ControllerService.getInstance();
    private final CaronaService caronaService = serviceProvider.get(CaronaService.class);

    private final CaronaRepo repo;
    private final ReservaRepo reservaRepo = caronaService.getReservaRepo();
    private final UserController userController;

    public CaronaController(CaronaRepo repo) {
        this.repo = repo;
        this.userController = serviceProvider.get(UserController.class);
    }

    public Response criarCarona(Request request) {
        String userID = validarMotorista(request.getToken());

        NovaCaronaRequest caronaDTO = JsonUtils.fromJson(request.getDados(), NovaCaronaRequest.class);

        if (!userID.equals(caronaDTO.idMotorista()))
            throw new ForbiddenException();

        Carona C = new Carona(
                new Rota(caronaDTO.trechos()),
                caronaDTO.idMotorista(),
                caronaDTO.vagas(),
                caronaDTO.dataPartida()
        );

        repo.salvar(C);

        return new Response(Status.CARONA_CRIADA);
    }

    @Deprecated
    public Response consultarCaronas(Request request) {
        String userID = validarMotorista(request.getToken());

        List<CaronaResponse> caronas = repo.getDadosList().stream()
                .filter(C -> C.getId_motorista().equals(userID))
                .map(this::toCaronaResponseDTO)
                .toList();

        return new Response(Status.SUCESSO, "MINHAS_CARONAS", JsonUtils.toJson(caronas));
    }

    public Response consultarPassageiros(Request request) {
        Carona C = encontrarCaronaValidado(request.getToken(), request.getDados());

        List<PassageiroPorTrechoResponse> passageiros = C.getOcupacaoPorTrecho().entrySet().stream()
                .map(E -> new PassageiroPorTrechoResponse(E.getKey(), getNomesPassageiros(E.getValue())))
                .toList();

        return new Response(Status.SUCESSO, "DADOS_PASSAGEIROS", JsonUtils.toJson(passageiros));
    }

    public Response cancelarCarona(Request request) {
        Carona C = encontrarCaronaValidado(request.getToken(), request.getDados());

        caronaService.cancelarCaronaEItinerarios(C);

        C.setStatus(StatusCarona.CANCELADA);

        return new Response(Status.CARONA_CANCELADA);
    }

    public Response cancelarReserva(Request request) {
        String userID = userController.validarUsuarioLogado(request.getToken());
        ReservaResponse reserva = JsonUtils.fromJson(request.getDados(), ReservaResponse.class);

        Set<String> caronasUnicas = reserva.itinerario().passos().stream()
                .map(PassoItinerarioDTO::idCarona)
                .collect(Collectors.toSet());

        for (String idCarona : caronasUnicas) {
            Carona carona = repo.getByID(idCarona);
            if (carona != null)
                carona.removerPassageiro(userID);
        }

        Reserva reservaReal = reservaRepo.getByID(reserva.idReserva());
        if (reservaReal != null) {
            reservaReal.setStatus(StatusReserva.CANCELADA);
        }

        List<ReservaResponse> minhasReservas = buscarReservasPassageiro(userID);
        return new Response(Status.RESERVA_CANCELADA, JsonUtils.toJson(minhasReservas));
    }

    @Deprecated
    public Response buscarItinerarios(Request request) {
        userController.validarUsuarioLogado(request.getToken());

        BuscaCaronaRequest buscarDTO = JsonUtils.fromJson(request.getDados(), BuscaCaronaRequest.class);

        List<List<ArestaTrecho>> caminhosBrutos = caronaService.buscarItinerarios(buscarDTO);

        List<ItinerarioDTO> itinerarioDTO = caronaService.toItinerariosDTO(caminhosBrutos);

        String jsonResposta = JsonUtils.toJson(itinerarioDTO);
        return new Response(Status.SUCESSO, "ITINERARIOS_BUSCADOS", jsonResposta);
    }

    @Deprecated
    public Response consultarMinhasReservas(Request request) {
        String userID = userController.validarUsuarioLogado(request.getToken());

        List<ReservaResponse> reservas = reservaRepo.getByUserId(userID).stream()
                .map(Reserva::toData)
                .toList();

        return new Response(
                Status.SUCESSO, "RESERVAS_PASSAGEIRO",
                JsonUtils.toJson(reservas)
        );
    }

    @Deprecated
    public Response confirmarReserva(Request request) {
        String userID = userController.validarUsuarioLogado(request.getToken());

        ItinerarioDTO itinerario = JsonUtils.fromJson(request.getDados(), ItinerarioDTO.class);

        List<ArestaTrecho> itinerarioEscolhido = itinerario.passos().stream()
                .map(passo -> {
                    Carona carona = repo.getByID(passo.idCarona());
                    if (carona == null) {
                        throw new IncorrectRequestException("Carona não encontrada.");
                    }

                    Trecho trechoObj = new Trecho(passo.inicio(), passo.fim());

                    return new ArestaTrecho(passo.fim(), carona, trechoObj);
                })
                .toList();

        boolean sucesso = caronaService.confirmarReservaAtomica(userID, itinerarioEscolhido, itinerario);

        return sucesso
                ? new Response(Status.RESERVA_CONFIRMADA)
                : new Response(Status.CONFLITO_RESERVA);
    }

    private List<ReservaResponse> buscarReservasPassageiro(String userID) {
        return reservaRepo.getByUserId(userID).stream()
                .filter(R -> R.getStatus() == StatusReserva.ATIVA)
                .map(Reserva::toData)
                .toList();
    }

    private String validarMotorista(String token) throws ForbiddenException, TokenInvalidoException {
        String userID = userController.validarUsuarioLogado(token);

        Usuario user = userController.getByID(userID);
        if (user == null) {
            System.err.println("Token inválido.");
            throw new TokenInvalidoException();
        }
        else if (!user.isMotorista()) throw new ForbiddenException("Usuário não é um motorista.");

        return userID;
    }

    private Carona encontrarCaronaValidado(String token, String idCarona) throws ForbiddenException, CaronaNaoEncontradaException {
        String userID = validarMotorista(token);

        Carona C = repo.getByID(idCarona);

        if (C == null) throw new NotFoundResponse("Carona não encontrada.");
        if (!C.getId_motorista().equals(userID))
            throw new ForbiddenException();

        return C;
    }

    private List<String> getNomesPassageiros(Set<String> value) {
        return value.stream()
                .map(userController::getByID)
                .map(Usuario::getNome)
                .toList();
    }

    private CaronaResponse toCaronaResponseDTO(Carona carona) {
        List<TrechoOcupacaoDTO> ocupacao = getPassageirosDTO(carona);

        return new CaronaResponse(
                carona.getId(), carona.getId_motorista(),
                carona.getVagasTotais(), carona.getData(),
                carona.getStatus().toString(),
                ocupacao
        );
    }

    List<TrechoOcupacaoDTO> getPassageirosDTO(Carona carona) {
        return carona.getOcupacaoPorTrecho().entrySet().stream()
                .map(E -> {
                    Set<OtherUserDTO> userDTOS = E.getValue().stream()
                            .map(userController::getByID)
                            .map(Usuario::getOtherData)
                            .collect(Collectors.toSet());
                    return new TrechoOcupacaoDTO(E.getKey(), userDTOS);
                })
                .toList();
    }

    public void buscarItinerarios(@NotNull Context context) {
        userController.validarUsuarioLogado(tokenDe(context));

        BuscaCaronaRequest busca = context.bodyAsClass(BuscaCaronaRequest.class);

        List<List<ArestaTrecho>> caminhosBrutos = caronaService.buscarItinerarios(busca);

        List<ItinerarioDTO> itinerarioDTO = caronaService.toItinerariosDTO(caminhosBrutos);

        context.json(itinerarioDTO);
    }

    public void consultarMinhasReservas(@NotNull Context context) {
        String userID = userController.validarUsuarioLogado(tokenDe(context));

        List<ReservaResponse> reservas = reservaRepo.getByUserId(userID).stream()
                .map(Reserva::toData)
                .toList();

        context.json(reservas);
    }

    public void confirmarReserva(@NotNull Context context) {
        String userID = userController.validarUsuarioLogado(tokenDe(context));

        ItinerarioDTO itinerario = context.bodyAsClass(ItinerarioDTO.class);

        List<ArestaTrecho> itinerarioEscolhido = itinerario.passos().stream()
                .map(passo -> {
                    Carona carona = repo.getByID(passo.idCarona());
                    if (carona == null) {
                        throw new IncorrectRequestException("Carona não encontrada.");
                    }

                    Trecho trechoObj = new Trecho(passo.inicio(), passo.fim());

                    return new ArestaTrecho(passo.fim(), carona, trechoObj);
                })
                .toList();

        boolean sucesso = caronaService.confirmarReservaAtomica(userID, itinerarioEscolhido, itinerario);

        context.status(sucesso ? HttpStatus.CREATED : HttpStatus.CONFLICT);
        context.json(sucesso ? "" : Map.of("erro", "Algum trecho ficou sem vagas."));
    }

    public void cancelarReserva(@NotNull Context context) {
        String userID = userController.validarUsuarioLogado(tokenDe(context));

        String reservaID = context.pathParam("id");

        Reserva reserva = reservaRepo.getByID(reservaID);
        if (reserva == null) throw new NotFoundResponse("Reserva não encontrada.");

        Set<String> caronasUnicas = reserva.getItinerario().passos().stream()
                .map(PassoItinerarioDTO::idCarona)
                .collect(Collectors.toSet());

        for (String idCarona : caronasUnicas) {
            Carona carona = repo.getByID(idCarona);
            if (carona != null)
                carona.removerPassageiro(userID);
        }

        reserva.setStatus(StatusReserva.CANCELADA);

        List<ReservaResponse> minhasReservas = buscarReservasPassageiro(userID);
        context.json(minhasReservas);
    }

    public void criarCarona(@NotNull Context context) {
        String userID = validarMotorista(tokenDe(context));
        NovaCaronaRequest carona = context.bodyAsClass(NovaCaronaRequest.class);

        if (!userID.equals(carona.idMotorista()))
            throw new ForbiddenException();

        Carona C = new Carona(
                new Rota(carona.trechos()),
                carona.idMotorista(),
                carona.vagas(),
                carona.dataPartida()
        );

        repo.salvar(C);

        context.status(HttpStatus.CREATED);
    }

    public void cancelarCarona(@NotNull Context context) {
        System.out.println("Cancelando...");
        Carona C = encontrarCaronaValidado(tokenDe(context), context.pathParam("id"));

        caronaService.cancelarCaronaEItinerarios(C);

        C.setStatus(StatusCarona.CANCELADA);

        context.status(HttpStatus.OK);

        System.out.println("Cancelada.");
    }

    public void consultarCaronas(@NotNull Context context) {
        String userID = validarMotorista(tokenDe(context));

        List<CaronaResponse> caronas = repo.getDadosList().stream()
                .filter(C -> C.getId_motorista().equals(userID))
                .map(this::toCaronaResponseDTO)
                .toList();

        context.json(caronas);
    }
}
