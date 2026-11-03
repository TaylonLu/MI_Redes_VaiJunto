package org.UEFS.Server.controller;


import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import org.UEFS.Server.domain.caronas.Carona;
import org.UEFS.Server.domain.caronas.CaronaRepo;
import org.UEFS.Server.domain.caronas.CaronaService;
import org.UEFS.Server.domain.caronas.Rota;
import org.UEFS.Server.domain.cidades.CidadeMapper;
import org.UEFS.Server.domain.reservas.TransacaoService;
import org.UEFS.Server.domain.usuarios.Usuario;
import org.UEFS.Server.exceptions.CaronaNaoEncontradaException;
import org.UEFS.Server.exceptions.DadosIncorretosException;
import org.UEFS.Server.exceptions.ForbiddenException;
import org.UEFS.Server.exceptions.TokenInvalidoException;
import org.UEFS.shared.JsonUtils;
import org.UEFS.shared.dto.OtherUserDTO;
import org.UEFS.shared.dto.Trecho;
import org.UEFS.shared.dto.TrechoOcupacaoDTO;
import org.UEFS.shared.dto.requests.NovaCaronaRequest;
import org.UEFS.shared.dto.requests.ReservaRequest;
import org.UEFS.shared.dto.responses.CaronaResponse;
import org.UEFS.shared.dto.responses.PassageiroPorTrechoResponse;
import org.UEFS.shared.dto.responses.ReservaResponse;
import org.UEFS.shared.enums.Status;
import org.UEFS.shared.model.Request;
import org.UEFS.shared.model.Response;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.UEFS.Server.http.Utils.tokenDe;

public class CaronaController {
    private static final Logger log = LoggerFactory.getLogger(CaronaController.class);
    protected final ControllerService serviceProvider = ControllerService.getInstance();
    private final CaronaService caronaService = serviceProvider.get(CaronaService.class);
    private final TransacaoService trService = serviceProvider.get(TransacaoService.class);

    private final CaronaRepo repo;
    private final UserController userController;
    private final CidadeMapper cidadeMapper;

    public CaronaController(CaronaRepo repo, CidadeMapper ciadeMapper) {
        this.repo = repo;
        this.cidadeMapper = ciadeMapper;
        this.userController = serviceProvider.get(UserController.class);
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

    private List<TrechoOcupacaoDTO> getPassageirosDTO(Carona carona) {
        List<TrechoOcupacaoDTO> ocupacao = new ArrayList<>();

        // O compilador Java nunca se confunde com esta sintaxe:
        for (Map.Entry<Trecho, Set<String>> entry: carona.getOcupacaoPorTrecho().entrySet()) {
            Set<String> idsPassageiros = entry.getValue();

            Set<OtherUserDTO> passageiros = idsPassageiros.stream()
                    .map(idPassageiro -> {
                        // Tenta encontrar na base de dados local
                        Usuario u = userController.getByID(idPassageiro);
                        if (u != null) return u.getOtherData();

                        // Se não encontrar, pergunta aos vizinhos
                        OtherUserDTO userRemoto = userController.getUserRemoto(idPassageiro);
                        return Objects.requireNonNullElseGet(userRemoto, () -> new OtherUserDTO(idPassageiro, "Email Externo", "Nome Externo"));

                    })
                    .collect(Collectors.toSet());

            ocupacao.add(new TrechoOcupacaoDTO(entry.getKey(), passageiros));
        }

        return ocupacao;
    }

    public void cancelarReserva(@NotNull Context context) {
        String idTransacao = context.pathParam("id");
        String userID = userController.validarUsuarioLogado(tokenDe(context));

        if (userID == null) throw new ForbiddenException("Passageiro não autenticado.");

        trService.cancelarReserva(idTransacao, userID);
        List<ReservaResponse> reservas = trService.consultarReservas(userID);

        context.status(200).json(reservas);
    }

    /**
     * Consulta as rese
     * @param context
     */
    public void consultarReservas(@NotNull Context context) {
        String userID = userController.validarUsuarioLogado(tokenDe(context));

        List<ReservaResponse> minhasReservas = trService.consultarReservas(userID);
        context.json(minhasReservas);
    }

    public void criarCarona(@NotNull Context context) {
        String token = tokenDe(context);
        String userID = validarMotorista(token);
        NovaCaronaRequest caronaDTO = context.bodyAsClass(NovaCaronaRequest.class);

        if (!userID.equals(caronaDTO.idMotorista())) {
            log.error("[CARONA] ID não bate com token. \n Token: {}\nID Encontrado: {}\nID dado: {}", token, userID, caronaDTO.idMotorista());
            throw new ForbiddenException("ID do motorista na requisição não bate com o dono do token.");
        }

        if (caronaDTO.trechos() == null || caronaDTO.trechos().isEmpty()) {
            context.status(400).json(Map.of("erro", "A rota precisa possuir pelo menos um trecho."));
            int origemInicial = caronaDTO.trechos().getFirst().origem();
            if (!cidadeMapper.ehCidadeDesteServidor(origemInicial)) {
                throw new ForbiddenException("Você só pode iniciar caronas a partir das cidades pertencentes a esta região.");
            }
            return;
        }

        if (caronaDTO.trechos().stream().anyMatch(Objects::isNull)) {
            context.status(400).json(Map.of("erro", "A rota contém trechos inválidos."));
            return;
        }

        if (caronaDTO.vagas() < 1) {
            context.status(400).json(Map.of("erro", "A quantidade de vagas deve ser maior que zero."));
            return;
        }

        if (!Double.isFinite(caronaDTO.valorPorTrecho()) || caronaDTO.valorPorTrecho() < 0) {
            context.status(400).json(Map.of("erro", "O valor por trecho é inválido."));
            return;
        }

        if (caronaDTO.dataPartida() == null || caronaDTO.dataPartida().isBefore(LocalDateTime.now())) {
            context.status(400).json(Map.of("erro", "A data de partida é inválida ou está no passado."));
            return;
        }

        Rota rota = new Rota(caronaDTO.trechos());

        if (!rota.isContinua()) {
            context.status(400).json(Map.of("erro", "Os trechos da rota não formam um percurso contínuo."));
            return;
        }

        Carona C = new Carona(
                rota,
                caronaDTO.idMotorista(),
                caronaDTO.vagas(),
                caronaDTO.valorPorTrecho(),
                caronaDTO.dataPartida()
        );

        repo.salvar(C);
        repo.salvarNoDisco();

        context.status(HttpStatus.CREATED).json(C);
    }

    public void cancelarCarona(@NotNull Context context) {
        String idCarona = context.pathParam("id");
        String token = tokenDe(context);
        String idMotorista = validarMotorista(token);

        if (idMotorista == null) {
            throw new ForbiddenException("Motorista não autenticado.");
        }

        caronaService.cancelarCaronaETransacoes(idCarona, idMotorista);
        trService.cancelarTransacoesPorCarona(idCarona);
        context.status(200).json(
                Map.of("mensagem", "Carona e reservas associadas canceladas"));
    }

    public void consultarCaronas(@NotNull Context context) {
        String userID = validarMotorista(tokenDe(context));

        List<CaronaResponse> caronas = repo.getDadosList().stream()
                .filter(C -> C.getId_motorista().equals(userID))
                .map(C -> {
                    caronaService.sincronizarPorCarona(C.getId());
                    return toCaronaResponseDTO(C);
                })
                .toList();

        context.json(caronas);
    }


    public CaronaRepo getCaronaRepo() {
        return repo;
    }
}
