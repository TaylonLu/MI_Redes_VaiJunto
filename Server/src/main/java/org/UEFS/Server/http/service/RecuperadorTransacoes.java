package org.UEFS.Server.http.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.UEFS.Server.domain.reservas.EstadoTransacao;
import org.UEFS.Server.domain.reservas.Transacao;
import org.UEFS.Server.domain.reservas.TransacaoService;
import org.UEFS.Server.http.cluster.PeerClient;
import org.UEFS.Server.http.relogio.ServerConstant;
import org.UEFS.shared.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.UEFS.Server.http.service.ConsorcioUtils.isVazio;

public class RecuperadorTransacoes {
    private static final long INTERVALO_SEGUNDOS = 5;
    private static final Logger log = LoggerFactory.getLogger(RecuperadorTransacoes.class);

    private final String meuId;
    private final TransacaoService transacoes;
    private final PeerClient peerClient;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    public RecuperadorTransacoes(String meuId, TransacaoService transacoes, PeerClient peerClient) {
        this.meuId = meuId;
        this.transacoes = transacoes;
        this.peerClient = peerClient;
    }

    /**
     * Inicia a recuperação periódica de transações.
     */
    public void iniciar() {
        executor.scheduleWithFixedDelay(this::recuperar,
                0, INTERVALO_SEGUNDOS,
                TimeUnit.SECONDS
        );

        log.info("[RECUPERAÇÃO] Recuperador de transações iniciado.");
    }

    /**
     * Analisa todas as transações persistidas e tenta resolver pendências.
     */
    private void recuperar() {
        for (Transacao tx : transacoes.listarTodas()) {
            try {
                if (tx.isEmProcessamento()) continue;

                switch (tx.getEstado()) {
                    case COMITADO -> propagarCommit(tx);
                    case ABORTADO -> propagarRollback(tx);
                    case PREPARADO -> recuperarPreparada(tx);
                    default -> {
                        // Estados de cancelamento não são dessa ossada.
                    }
                };
            } catch (RuntimeException e) {
                log.error("[RECUPERAÇÃO] Erro ao recuperar transação '{}': {}", tx.getId(), e.getMessage(), e);
            }
        }
    }

    private void recuperarPreparada(Transacao tx) {
        if (tx.souDono(meuId)) { // Sou dono, não tem um servidor anterior para tomar decisões.
            log.warn("[RECUPERAÇÃO] Coordenador encontrou transação PEREPARADO '{}'", tx.getId());
            abortar(tx);
            return;
        }

        // Participantes precisam consultar o servidor anterior.
        String anteriorId = tx.getAnterior();

        if (isVazio(anteriorId)) {
            log.warn("[RECUPERAÇÃO] Transação '{}' sem servidor anterior. Aguardando...", tx.getId());
            return;
        }

        Vizinho anterior = ServerConstant.getVizinho(anteriorId);

        PeerClient.ResultadoStatus resp = peerClient.consultarStatusTransacao(anterior, tx.getId());
        if (resp.falhaRede()) {
            log.warn("[RECUPERAÇÃO] Não foi possível consultar '{}' para a transação '{}'", anteriorId, tx.getId());
            return;
        }

        // Se o anterior não conhece a transação, não existe decisão
        // de commit registrada nele.
        if (resp.inexistente()) {
            log.warn("[RECUPERAÇÃO] Não foi possível consultar '{}' para a transação '{}'.", anteriorId, tx.getId());
            return;
        }

        if (!resp.conhecido()) {
            log.warn("[RECUPERAÇÃO] Servidor anterior respondeu HTTP {} para '{}'.", resp.http(), tx.getId());
            return;
        }

        try {
            JsonNode json = JsonUtils.getMapper().readTree(resp.corpo());
            String estadoTexto = json.path("status").asText(null);

            if (isVazio(estadoTexto)) {
                log.warn("[RECUPERAÇÃO] Resposta sem estado para '{}'.", tx.getId());
                return;
            }

            EstadoTransacao estadoAnterior = EstadoTransacao.valueOf(estadoTexto);

            switch (estadoAnterior) {
                case COMITADO -> {
                    log.info("[RECUPERAÇÃO] Anterior confirmou '{}'. Efetivando commit.", tx.getId());
                        if (transacoes.decidir(tx.getId(), EstadoTransacao.COMITADO)
                                == Resultado.OK) {
                            propagarCommit(tx);
                        }
                } case ABORTADO ->  {
                    log.info("[RECUPERAÇÃO] Anterior abortou '{}'. Efetivando rollback.", tx.getId());
                    abortar(tx);
                }
                default -> log.info("[RECUPERAÇÃO] Anterior ainda não decidiu '{}': {}", tx.getId(), estadoAnterior);
            }


        } catch (Exception e) {
            log.error("[RECUPERAÇÃO] Estado remoto inválido para '{}': {}", tx.getId(), e.getMessage());
        }
    }

    /**
     * Aborta localmente e propaga a decisão.
     */
    private void abortar(Transacao tx) {
        Resultado res = transacoes.desfazer(tx.getId());

        if (res == Resultado.OK) propagarRollback(tx);
        else {
            log.error("[RECUPERAÇÃO] Não foi possível abortar '{}': {}", tx.getId(), res);
        }
    }

    /**
     * Reenvia commit ao próximo servidor. */
    private void propagarCommit(Transacao tx) {
        propagar(tx, true);
    }

    /**
     * Reenvia rollback ao próximo servidor. */
    private void propagarRollback(Transacao tx) {
        propagar(tx, false);
    }

    /**
     * Propaga uma decisão já registrada localmente.
     */
    private void propagar(Transacao tx, boolean commit) {
        String proximoId = tx.getProximo();

        if (isVazio(proximoId)) return;

        Vizinho proximo = ServerConstant.getVizinho(proximoId);

        if (proximo == null) {
            log.warn("[RECUPERAÇÃO] Próximo servidor '{}' não encontrado para '{}'", proximoId, tx.getId());
            return;
        }

        boolean sucesso = commit
                ? peerClient.commit(proximo, tx.getId())
                : peerClient.rollback(proximo, tx.getId());

        if (!sucesso) {
            log.warn("[RECUPERAÇÃO] Falha ao propagar {} de '{}' para '{}' será tentado novamente.",
                    commit ? "COMMIT" : "ROLLBACK", tx.getId(), proximo);
        }
    }

    /**
     * Encerra o agendador.
     */
    public void encerrar() { executor.shutdown(); }
}
