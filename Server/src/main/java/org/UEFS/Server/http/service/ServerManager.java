package org.UEFS.Server.http.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.Context;
import org.UEFS.Server.controller.CaronaController;
import org.UEFS.Server.controller.ControllerService;
import org.UEFS.Server.controller.UserController;
import org.UEFS.Server.domain.cidades.CidadeMapper;
import org.UEFS.Server.domain.reservas.EstadoTransacao;
import org.UEFS.Server.http.relogio.*;
import org.UEFS.shared.JsonUtils;
import org.UEFS.shared.dto.consorcio.CancelamentoRequest;
import org.UEFS.shared.dto.consorcio.ReservaClienteRequest;
import org.UEFS.shared.dto.consorcio.ReservaRequest;
import org.UEFS.Server.http.service.ConsorcioUtils.RespostaConsorcio;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.stream.Collectors;

import static org.UEFS.Server.http.Utils.tokenDe;
import static org.UEFS.Server.http.service.ConsorcioUtils.isVazio;


public class ServerManager {
    private static final Logger log = LoggerFactory.getLogger(ServerManager.class);
    private final CaronaController caronaController = ControllerService.getInstance().get(CaronaController.class);
    private final UserController userController = ControllerService.getInstance().get(UserController.class);

    private final RelogioLamport relogio;
    private final BuscaDistribuida buscaService;
    private final ReservaParticipante participante;
    private final ReservaCoordenador coordenador;

    public ServerManager(BuscaDistribuida buscaService,
                         RelogioLamport relogio,
                         ReservaParticipante participante,
                         ReservaCoordenador coordenador) {
        this.relogio = relogio;
        this.buscaService = buscaService;
        this.participante = participante;
        this.coordenador = coordenador;
    }

    public void health(@NotNull Context contexto) {
        contexto.json(Map.of(
                "status", ServerConstant.getMeuId() + " ta on",
                "relogio", relogio.getTempo())
        );
    }

    // POST /api/cliente/reservas || coordenador do 2PC
    public void reservaCliente(@NotNull Context ctx) {
        String token = tokenDe(ctx);
        String idPassageiro = userController.validarUsuarioLogado(token);

        ReservaClienteRequest pedido;
        try {
            pedido = ctx.bodyAsClass(ReservaClienteRequest.class);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("erro", "Corpo JSON inválido para reserva"));
            return;
        }

        RespostaConsorcio resposta = coordenador.reservar(idPassageiro, pedido);
        ctx.status(resposta.status()).json(resposta.corpo());
    }

    /** GET /api/consorcio/trechos: chamado pelos vizinhos; não consulta vizinhos a partir da origem. */
    public void trechos(@NotNull Context ctx) {
        responderBusca(ctx, false, ctx.queryParam("visitados"));
    }

    /** GET /api/cliente/itinerarios: quem inicia a busca; também consulta os vizinhos a partir da origem. */
    public void itinerarios(@NotNull Context ctx) {
        responderBusca(ctx, true, null);
    }

    public void responderBusca(Context context, boolean incluirRemotoOrigem, String visitados) {
        String origem = context.queryParam("origem");
        String destino = context.queryParam("destino");
        String data = context.queryParam("data");
        String vagas = context.queryParam("vagas");

        if (isVazio(origem) || isVazio(destino) ||
                isVazio(data) || isVazio(vagas)) {
            context.status(400).json(Map.of(
                    "erro", "Informe origem, destino, data e vagas."
            ));
            return;
        }

        LocalDate dataDesejada;
        int vagasRequeridas;

        try {
            dataDesejada = LocalDate.parse(data);
            vagasRequeridas = Integer.parseInt(vagas);

            if (vagasRequeridas < 1) {
                throw new NumberFormatException();
            }
        } catch (DateTimeParseException | NumberFormatException e) {
            context.status(400).json(Map.of(
                    "erro", "Data ou quantidade de vagas inválida."
            ));
            return;
        }

        context.json(buscaService.executar(origem, destino,
                dataDesejada,
                vagasRequeridas,
                visitados,
                incluirRemotoOrigem
        ));
    }

    public void prepare(@NotNull Context ctx) {
        ReservaRequest pedido;
        try {
            pedido = ctx.bodyAsClass(ReservaRequest.class);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("erro", "Corpo JSON invalido"));
            return;
        }

        RespostaConsorcio resposta = participante.prepare(pedido, ctx.header("X-Server-Id"));
        ctx.status(resposta.status()).json(resposta.corpo());
    }

    public void rollback(@NotNull Context ctx) {
        RespostaConsorcio resposta = participante.rollback(ctx.pathParam("id"));
        ctx.status(resposta.status()).json(resposta.corpo());
    }

    public void commit(@NotNull Context ctx) {
        RespostaConsorcio resposta = participante.commit(ctx.pathParam("id"));
        ctx.status(resposta.status()).json(resposta.corpo());
    }

    public void cancel(@NotNull Context ctx) {
        RespostaConsorcio resposta = participante.cancel(
                ctx.pathParam("id"),
                EstadoTransacao.CANCELADO_PELO_PASSAGEIRO,
                ctx.header("X-Server-Id")
        );

        ctx.status(resposta.status()).json(resposta.corpo());
    }

    public void cancelMotorista(@NotNull Context ctx) {
        // Repassa o ID e resgata quem enviou o aviso pelo header X-Server-Id
        ConsorcioUtils.RespostaConsorcio resposta =
                participante.cancelMotorista(
                        ctx.pathParam("id"),
                        ctx.header("X-Server-Id")
                );

        ctx.status(resposta.status()).json(resposta.corpo());
    }

    public void status(@NotNull Context ctx) {
        RespostaConsorcio resposta = participante.status(ctx.pathParam("id"));
        ctx.status(resposta.status()).json(resposta.corpo());
    }

    public void getCidades(@NotNull Context context) {
        CidadeMapper mapper = ControllerService.getInstance().get(CidadeMapper.class);
        Map<Integer, String> cidades = ServerConstant.getRegiao().getCidades().stream()
                .collect(Collectors.toMap(
                        mapper::traduzirParaId,
                        C -> C
                ));
        context.header("X-Server-Region",ServerConstant.getRegiao().regiaoBase());
        context.json(cidades);
    }

    /** before de /api/*: atualiza o relógio com o header, ou só avança se faltar ou for inválido. */
    public void aoReceber(@NotNull Context ctx) {
        String header = ctx.header("X-Lamport-Clock");
        try {
            if (header != null) relogio.atualizar(Integer.parseInt(header.trim()));
            else relogio.tique();
        } catch (NumberFormatException e) {
            relogio.tique();
        }
    }

    /**
     * After de {@code /api*}: toda resposta leva o id da região e do relógio.
     */
    public void aoResponder(@NotNull Context ctx) {
        ctx.header("X-Lamport-Clock", String.valueOf(relogio.getTempo()));
        ctx.header("X-Server-Id", ServerConstant.getMeuId());
    }
}
