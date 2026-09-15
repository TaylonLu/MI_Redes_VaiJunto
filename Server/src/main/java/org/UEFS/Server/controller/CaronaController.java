package org.UEFS.Server.controller;


import org.UEFS.Server.domain.caronas.*;
import org.UEFS.Server.domain.usuarios.Usuario;
import org.UEFS.Server.exceptions.*;
import org.UEFS.shared.JsonUtils;
import org.UEFS.shared.dto.ItinerarioDTO;
import org.UEFS.shared.dto.OtherUserDTO;
import org.UEFS.shared.dto.Trecho;
import org.UEFS.shared.dto.TrechoOcupacaoDTO;
import org.UEFS.shared.dto.requests.BuscaCaronaRequest;
import org.UEFS.shared.dto.requests.NovaCaronaRequest;
import org.UEFS.shared.dto.requests.ReservaRequest;
import org.UEFS.shared.dto.responses.CaronaResponse;
import org.UEFS.shared.dto.responses.PassageiroPorTrechoResponse;
import org.UEFS.shared.enums.Status;
import org.UEFS.shared.enums.StatusCarona;
import org.UEFS.shared.model.Request;
import org.UEFS.shared.model.Response;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        String userID = validarMotorista(request.getToken());

        NovaCaronaRequest caronaDTO = JsonUtils.fromJson(request.getDados(), NovaCaronaRequest.class);

        if (!userID.equals(caronaDTO.idMotorista()))
            throw new ForbiddenException();

        Carona C = new Carona(
                new Rota(caronaDTO.trechos()),
                caronaDTO.idMotorista(),
                caronaDTO.capacidade(),
                caronaDTO.data()
        );

        repo.salvar(C);

        return new Response(Status.CARONA_CRIADA);
    }

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

        C.setStatus(StatusCarona.CANCELADA);

        return new Response(Status.CARONA_CANCELADA);
    }

    public Response cancelarItinerario(Request request) {
        String userID = userController.validarUsuarioLogado(request.getToken());
        String idCarona = request.getDados().trim();

        Carona carona = repo.getByID(idCarona);
        if (carona == null) {
            throw new RecursoNaoEncontradoException("ID de carona dado não corresponde a nenhuma no banco de dados");
        }

        boolean removeu = carona.removerPassageiroCompletamente(userID);
        if (!removeu) throw new ReservaNaoEncontradaException();

        List<CaronaResponse> minhasReservas = buscarCaronasPassageiro(userID);
        return new Response(Status.RESERVA_CANCELADA, JsonUtils.toJson(minhasReservas));
    }

    public Response buscarItinerarios(Request request) {
        userController.validarUsuarioLogado(request.getToken());

        BuscaCaronaRequest buscarDTO = JsonUtils.fromJson(request.getDados(), BuscaCaronaRequest.class);

        List<List<ArestaTrecho>> caminhosBrutos = caronaService.buscarItinerarios(buscarDTO);

        List<ItinerarioDTO> itinerarioDTO = caronaService.toItinerariosDTO(caminhosBrutos);

        String jsonResposta = JsonUtils.toJson(itinerarioDTO);
        return new Response(Status.SUCESSO, "ITINERARIOS_BUSCADOS", jsonResposta);
    }

    public Response consultarMinhasReservas(Request request) {
        String userID = userController.validarUsuarioLogado(request.getToken());

        List<CaronaResponse> minhasReservas = buscarCaronasPassageiro(userID);

        return new Response(
                Status.SUCESSO, "RESERVAS_PASSAGEIRO",
                JsonUtils.toJson(minhasReservas)
        );
    }

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

        boolean sucesso = caronaService.confirmarReservaAtomica(userID, itinerarioEscolhido);

        return sucesso
                ? new Response(Status.RESERVA_CONFIRMADA)
                : new Response(Status.CONFLITO_RESERVA);
    }

    private List<CaronaResponse> buscarCaronasPassageiro(String userID) {
        return repo.getDadosList().stream()
                .filter(carona -> carona.getOcupacaoPorTrecho().values().stream()
                        .anyMatch(passageiros -> passageiros.contains(userID)))
                .map(this::toCaronaResponseDTO)
                .toList();
    }

    private String validarMotorista(String token) throws ForbiddenException, TokenInvalidoException {
        String userID = userController.validarUsuarioLogado(token);

        Usuario user = userController.getByID(userID);
        if (user == null) throw new TokenInvalidoException();
        else if (!user.isMotorista()) throw new ForbiddenException("Usuário não é um motorista.");

        return userID;
    }

    private Carona encontrarCaronaValidado(String token, String idCarona) throws ForbiddenException, CaronaNaoEncontradaException {
        String userID = validarMotorista(token);

        Carona C = repo.getByID(idCarona);

        if (C == null) throw new CaronaNaoEncontradaException();
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
}
