package org.UEFS.Server.http;

import io.javalin.http.Context;
import org.UEFS.Server.controller.CaronaController;
import org.UEFS.Server.controller.ControllerService;
import org.UEFS.Server.http.relogio.CMessage;
import org.UEFS.Server.http.relogio.EstadoLocal;
import org.UEFS.Server.http.relogio.MessageType;
import org.UEFS.Server.http.relogio.ServerConstant;
import org.UEFS.shared.dto.servers.ReservaRequest;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ServerManager {
    private static final Logger log = LoggerFactory.getLogger(ServerManager.class);
    private final EstadoLocal estado = ControllerService.getInstance().get(EstadoLocal.class);
    private final CaronaController caronaController = ControllerService.getInstance().get(CaronaController.class);

    public void health(@NotNull Context contexto) {
        contexto.json(new CMessage(
                MessageType.REPLY,
                ServerConstant.getMEU_ID(),
                estado.getClock())
        );
    }

    public void trechos(@NotNull Context context) {
        String origem = context.queryParam("origem");
        String destino = context.queryParam("destino");
        String visitados = context.queryParam("visitados");

        if (origem == null || destino == null)
            throw new IllegalArgumentException("Dados de origem e destino incorretos.");

        log.info("[SERVIDOR] [BUSCA] {} -> {} || visitados {}",
                origem, destino, visitados);

        String json = """
                {
                  "itinerario_id": "stub",
                  "trechos": [
                    {
                      "servidor": "%s",
                      "id_carona": "stub-01",
                      "origem": "%s",
                      "destino": "%s",
                      "preco": 0.0
                    }
                  ]
                }""".formatted(ServerConstant.getMEU_ID(), origem, destino);

        context.result(json);
    }

    public void prepare(@NotNull Context contexto) {
        ReservaRequest reserva;

        try { reserva = contexto.bodyAsClass(ReservaRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Corpo sem dados necessarios.");
        }

        if (caronaController.getCaronaRepo().getByID(reserva.id_transacao()) != null) {
            log.info("[SERVIDOR] [CARONA] carona ");
        }

        log.info("[SERVIDOR] [RESERVA] ");
    }
}
