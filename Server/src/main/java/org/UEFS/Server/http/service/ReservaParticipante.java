package org.UEFS.Server.http.service;

import org.UEFS.Server.domain.caronas.IdPerna;
import org.UEFS.Server.domain.reservas.EstadoTransacao;
import org.UEFS.Server.domain.reservas.Transacao;
import org.UEFS.Server.domain.reservas.TransacaoService;
import org.UEFS.Server.http.cluster.Repasse;
import org.UEFS.Server.http.cluster.ResultadoRepasse;
import org.UEFS.Server.http.relogio.ServerConstant;
import org.UEFS.shared.dto.consorcio.*;
import org.UEFS.shared.model.ServerException;

import java.util.*;
import java.util.regex.Pattern;

import static org.UEFS.Server.http.service.ConsorcioUtils.*;

public class ReservaParticipante {
    private static final Pattern ID_VALIDO = Pattern.compile("[A-Za-z0-9._-]+");
    private static final int MAX_TRECHOS = 8;

    private final String meuId;
    private final List<Vizinho> vizinhos;
    private final TransacaoService transacoes;
    private final Repasse repasse;

    public ReservaParticipante(String meuId, List<Vizinho> vizinhos, TransacaoService transacoes, Repasse repasse) {
        this.meuId = meuId;
        this.vizinhos = vizinhos;
        this.transacoes = transacoes;
        this.repasse = repasse;
    }

    /**
     * Com base no pedido, tranca os assentos locais e repassa o resto do
     * pacote de viágem para o próximo vizinho da cadeia (2PC encadeado).
      * @param pedido Reserva contendo os trechos pedidos e dados da transação.
     * @param remetente O servidor que repassou o pedido.
     * @return Resposta do próximo servidor.
     */
    public RespostaConsorcio prepare(ReservaRequest pedido, String remetente) {
        if (pedido == null) throw new ServerException(400, "Corpo JSON invalido");
        String id = pedido.idTransacao();
        if (!idValido(id)) throw new ServerException(400, "id_transacao ausente ou invalido");
        if (isVazio(pedido.coordenadorId())) throw new ServerException(400, "Corpo sem corrdenador_id");
        List<TrechoSolicitado> lista = pedido.trechosSolicitados();
        if (lista == null || lista.isEmpty() || lista.size() > MAX_TRECHOS)
            throw new ServerException(400, "trechos_solicitados deve ter 1 a 8 itens");

        List<TrechoSolicitado> trechos = new ArrayList<>();
        for (TrechoSolicitado T : lista) {
            if (T == null || isVazio(T.servidor()) || !idValido(T.idCarona()))
                throw new ServerException(400, "trecho sem servidor ou com id_carona invalido");
            trechos.add(new TrechoSolicitado(ServerConstant.normalizar(T.servidor()), T.idCarona()));
        }

        int qtdMeusTrechos = 0;
        Set<String> caronasVistas = new HashSet<>();
        while (qtdMeusTrechos < trechos.size() && trechos.get(qtdMeusTrechos).servidor().equals(meuId)) {
            if (!caronasVistas.add(IdPerna.idBase(trechos.get(qtdMeusTrechos).idCarona())))
                throw new ServerException(400, "carona repetida na transacao");
            qtdMeusTrechos++;
        }

        List<TrechoSolicitado> trechosDosProximo = trechos.subList(qtdMeusTrechos, trechos.size());
        for (TrechoSolicitado T : trechosDosProximo) {
            if (T.servidor().equals(meuId))
                throw new ServerException(400, "O proximo servidor da cadeia nao faz fronteira com este");
        }

        Vizinho proximo = null;
        if (!trechosDosProximo.isEmpty()) {
            proximo = buscarVizinho(trechosDosProximo.getFirst().servidor());
            if (proximo == null) throw new ServerException(400, "O proximo servidor da cadeia não faz fronteira com este");
        }

        String servidorAnterior = ServerConstant.normalizar(isVazio(remetente) ? pedido.coordenadorId() : remetente);
        List<TrechoReserva> meusTrechos = new ArrayList<>();
        for (TrechoSolicitado T : trechos.subList(0, qtdMeusTrechos)) {
            meusTrechos.add(new TrechoReserva(T.servidor(), T.idCarona(), 0));
        }

        Transacao transacao = new Transacao(id, pedido.coordenadorId(), servidorAnterior,
                            proximo == null ? "" : proximo.id(), pedido.passageiro(),
                            pedido.relogioLogico(), meusTrechos
        );

        Preparo preparo = transacoes.preparar(transacao);
        if (preparo.resultado() != Resultado.OK)
            return respostaRecusa(toHttpStatus(preparo.resultado()), id, preparo.motivo());


        List<TrechoReserva> assentosAcumulados = new ArrayList<>(preparo.assentos());
        if (!preparo.repetido() && proximo != null) {
            ResultadoRepasse rep;
            try {
                ReservaRequest pedidoRestante = new ReservaRequest(id, pedido.coordenadorId(), pedido.passageiro(),
                        pedido.relogioLogico(), List.copyOf(trechosDosProximo));
                rep = repasse.prepare(proximo, pedidoRestante, contarServidoresConsecutivos(trechosDosProximo));
            } catch (RuntimeException e) {
                transacoes.desfazer(id);
                try { repasse.rollback(proximo, id); } catch (RuntimeException _) {}
                return respostaRecusa(500, id, "erro interno ao repassar o prepare");
            }

            if (!rep.ok()) {
                transacoes.desfazer(id);
                if (rep.falhaDeRede()) repasse.rollback(proximo, id);
                return respostaRecusa(rep.http(), id, rep.motivo());
            }

            assentosAcumulados.addAll(rep.assentos());
        }

        if (!preparo.repetido()) transacoes.terminarProcessamento(id);
        return builder()
                .put("id_transacao", id)
                .put("status", "READY")
                .put("servidor", meuId)
                .put("assentos", assentosAcumulados)
                .build(200);
    }

    /**
     * Desfaz as vagas trancadas na Fase 1 e passa o aborto adiante.
     */
    public RespostaConsorcio rollback(String idTransacao) {
        if (!idValido(idTransacao)) throw new ServerException(400, "id da transacao invalido");
        Transacao existente = transacoes.buscar(idTransacao);
        Resultado R = transacoes.desfazer(idTransacao);
        if (R == Resultado.CONFLITO) throw new ServerException(409, "rollback recusado: a transacao já foi confirmada");
        if (R != Resultado.OK) throw new ServerException(500, "falha ao desfazer a transacao");

        if (existente != null && !isVazio(existente.getProximo())) {
            Vizinho proximo = buscarVizinho(existente.getProximo());
            if (proximo != null) repasse.rollback(proximo, idTransacao);
        }

        return builder()
                .put("id_transacao", idTransacao)
                .put("mensagem", "Transacao abortada com sucesso")
                .build(200);
    }

    /** Efetiva a transação localmente e manda uma ordem de <i>commit</i>
     * para o próximo vizinho. */
    public RespostaConsorcio commit(String idTransacao) {
        if (!idValido(idTransacao)) throw new ServerException(400, "id da transacao invalido");

        Transacao existente = transacoes.buscar(idTransacao);
        if (existente == null) {
            throw new ServerException(404, "Transacao desconhecida (nao houve prepare)");
        }

        // Gravar decisão
        Resultado R = transacoes.decidir(idTransacao, EstadoTransacao.COMITADO);
        if (R != Resultado.OK)
            throw new ServerException(500, "Falha ao gravar a reserva em disco");

        // Pega o proximo vizinho para a propagação
        boolean propagacaoPendente = false;
        if (!isVazio(existente.getProximo())) {
            Vizinho proximo = buscarVizinho(existente.getProximo());
            if (proximo != null) {
                boolean entregue = repasse.commit(proximo, idTransacao);
                propagacaoPendente = !entregue;
            }
        }

        return builder()
                .put("id_transacao", idTransacao)
                .put("mensagem", "Transacao comitada com sucesso")
                .put("propagacao_pendente", propagacaoPendente)
                .build(200);
    }

    /**
     * Intercepta a requisição HTTP {@code PUT /cancel} da rede e delega a limpeza
     * inteligente para o método receber {@code ReceberCancelamentoDaRede}.
     */
    public RespostaConsorcio cancel(String idTransacao) {
        if (!idValido(idTransacao)) throw new ServerException(400, "id da transacao invalido");

        Transacao existente = transacoes.buscar(idTransacao);
        if (existente == null) {
            return ConsorcioUtils.builder()
                    .put("id_transacao", idTransacao)
                    .put("mensagem", "Já cancelada")
                    .build(200);
        }

        transacoes.receberCancelamentoDaRede(idTransacao);

        return ConsorcioUtils.builder()
                .put("id_transacao", idTransacao)
                .put("mensagem", "Reserva CANCELADA com sucesso.")
                .build(200);
    }

    public ConsorcioUtils.RespostaConsorcio status(String idTransacao) {
        Transacao existente = transacoes.buscar(idTransacao);
        if (existente == null) return respostaErro(404, "Transacao desconhecida");

        return ConsorcioUtils.builder()
                .put("id_transacao", idTransacao)
                .put("mensagem", "Reserva CANCELADA.")
                .put("status", existente.getEstado())
                .put("coordenador_id", existente.getCoordenadorId())
                .build(200);

    }
}

