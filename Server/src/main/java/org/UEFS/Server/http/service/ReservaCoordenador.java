package org.UEFS.Server.http.service;

import org.UEFS.Server.domain.caronas.IdPerna;
import org.UEFS.Server.domain.reservas.EstadoTransacao;
import org.UEFS.Server.domain.reservas.Transacao;
import org.UEFS.Server.domain.reservas.TransacaoService;
import org.UEFS.Server.http.cluster.Repasse;
import org.UEFS.Server.http.cluster.ResultadoRepasse;
import org.UEFS.Server.http.relogio.RelogioLamport;
import org.UEFS.shared.dto.consorcio.ReservaRequest;
import org.UEFS.shared.dto.consorcio.TrechoReserva;
import org.UEFS.shared.dto.consorcio.TrechoSolicitado;

import java.util.*;

import static org.UEFS.Server.http.relogio.ServerConstant.normalizar;
import static org.UEFS.Server.http.service.ConsorcioUtils.*;

public class ReservaCoordenador {
    private final String meuId;
    private final TransacaoService transacoes;
    private final RelogioLamport relogio;
    private final Repasse repasse;

    private static final int MAX_ITINERARIOS = 8;

    private record ResultadoValidacao(boolean ok, int status, String motivo, List<TrechoSolicitado> trechos, int qtdLocais, Vizinho proximoVizinho) {
        static ResultadoValidacao erro(int status, String motivo) {
            return new ResultadoValidacao(false, status, motivo, null, 0, null);
        }
    }

    public ReservaCoordenador(String meuId, TransacaoService transacoes, Repasse repasse, RelogioLamport relogio) {
        this.meuId = meuId;
        this.transacoes = transacoes;
        this.repasse = repasse;
        this.relogio = relogio;
    }

    /**
     * Executa o fluxo completo do 2PC.</div>
     * Tranca as vagas locais ({@code prepare}), repassa o pedido aos vizinhos
     * ({@code repasse.prepare}) e, se todos aceitarem, manda confirmar
     * ({@code repasse.commit}). Se alguém recusar, manda desfazer({@code repasse.rollback})
     * @param idPassageiro ID do passageiro que pediu a reserva.
     * @param pedidoCliente Pedido de reserva, {@link ReservaRequest} do cliente.
     * @return {@link RespostaConsorcio} contendo um mapa de informações.
     * Se deu cerro ou errado (e o porquê).
     */
    public ConsorcioUtils.RespostaConsorcio reservar(String idPassageiro, ReservaRequest pedidoCliente) {
        List<TrechoSolicitado> trechosBrutos = pedidoCliente.trechosSolicitados();

        ResultadoValidacao val = validarItinerario(trechosBrutos);
        if (!val.ok()) return respostaRecusa(val.status(), meuId, val.motivo());

        List<TrechoSolicitado> trechos = val.trechos();
        int qtdLocais = val.qtdLocais();
        Vizinho proximo = val.proximoVizinho();

        String idTransacao = UUID.randomUUID().toString();
        int timestamp = relogio.tique();
        Transacao transacao = transacoes.criarTransacaoLocal(idTransacao, idPassageiro, timestamp, trechos.subList(0, qtdLocais), proximo);


        // Prepare
        Preparo preparo = transacoes.preparar(transacao);
        if (preparo.resultado() != Resultado.OK) {
            return respostaRecusa(toHttpStatus(preparo.resultado()), idTransacao, preparo.motivo());
        }

        List<TrechoReserva> assentosConfirmados = new ArrayList<>(preparo.assentos());

        if (proximo != null) {
            List<TrechoSolicitado> remotos = trechos.subList(qtdLocais, trechos.size());
            ResultadoRepasse rep = repasse.prepare(proximo,
                    new ReservaRequest(idTransacao, meuId, idPassageiro, timestamp, remotos),
                    contarServidoresConsecutivos(remotos)
            );

            if (!rep.ok()) {
                transacoes.desfazer(idTransacao);
                if (rep.falhaDeRede()) repasse.rollback(proximo, idTransacao);
                return respostaRecusa(rep.http(), idTransacao, rep.motivo());
            }
            assentosConfirmados.addAll(rep.assentos());
        }

        // COMMIT
        if (transacoes.decidir(idTransacao, EstadoTransacao.COMITADO, assentosConfirmados) != Resultado.OK) {
            transacoes.desfazer(idTransacao);
            if (proximo != null) repasse.rollback(proximo, idTransacao);
            return respostaRecusa(500, meuId, "Falha ao gravar decisão e commit em disco");
        }

        transacoes.terminarProcessamento(idTransacao);

        boolean propagacaoPendente = (proximo != null) && !repasse.commit(proximo, idTransacao);

        return respostaSucesso(idTransacao, idPassageiro, timestamp, propagacaoPendente, assentosConfirmados);
    }

    /**
     * Valida a topologia física antes de trancar as vagas. </div>
     * Garantindo que os trechos fazem fronteira, que não há <i>loops</i>
     * e que a sequência é válida.
     * @param trechosBrutos Lista de trechos do pedido do cliente.
     * @return {@link ResultadoValidacao} contendo informações do pedido.
     */
    private ResultadoValidacao validarItinerario(List<TrechoSolicitado> trechosBrutos) {
        if (trechosBrutos == null || trechosBrutos.isEmpty() || trechosBrutos.size() > MAX_ITINERARIOS) {
            return ResultadoValidacao.erro(400, "O itinerario deve ter entre 1 e 8 trechos");
        }

        List<TrechoSolicitado> trechos = new ArrayList<>();
        Set<String> caronasVistas = new HashSet<>();
        String servidorAnterior = meuId;

        for (int i = 0; i < trechosBrutos.size(); i++) {
            TrechoSolicitado T = trechosBrutos.get(i);
            String serv = normalizar(T.servidor());
            trechos.add(new TrechoSolicitado(serv, T.idCarona()));

            if (!caronasVistas.add(IdPerna.idBase(T.idCarona()) + "@" + serv)) {
                return ResultadoValidacao.erro(400, "Carona repetida no itinerario");
            }

            if (serv.equals(meuId) && i != 0 && !servidorAnterior.equals(meuId)) {
                return ResultadoValidacao.erro(400, "Os trechos deste servidor devem vir primeiro no itinerario");
            }

            if (!serv.equals(servidorAnterior) && servidorAnterior.equals(meuId) && buscarVizinho(serv) == null) {
                return ResultadoValidacao.erro(400, servidorAnterior + " e " + serv + " nao fazem fronteira");
            }

            servidorAnterior = serv;
        }

        int qtdLocais = 0;
        while (qtdLocais < trechos.size() && trechos.get(qtdLocais).servidor().equals(meuId))
            qtdLocais++;

        Vizinho proximo = (qtdLocais < trechos.size())
                ? buscarVizinho(trechos.get(qtdLocais).servidor())
                : null;

        return new ResultadoValidacao(true, 200, null, trechos, qtdLocais, proximo);
    }
}
