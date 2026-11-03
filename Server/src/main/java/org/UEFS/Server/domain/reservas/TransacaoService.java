package org.UEFS.Server.domain.reservas;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.UEFS.Server.controller.ControllerService;
import org.UEFS.Server.domain.caronas.Carona;
import org.UEFS.Server.domain.caronas.CaronaRepo;
import org.UEFS.Server.domain.caronas.CaronaService;
import org.UEFS.Server.domain.caronas.IdPerna;
import org.UEFS.Server.exceptions.ForbiddenException;
import org.UEFS.Server.exceptions.RecursoNaoEncontradoException;
import org.UEFS.Server.http.cluster.PeerClient;
import org.UEFS.Server.http.relogio.ServerConstant;
import org.UEFS.Server.http.service.Perna;
import org.UEFS.Server.http.service.Preparo;
import org.UEFS.Server.http.service.Resultado;
import org.UEFS.Server.http.service.Vizinho;
import org.UEFS.shared.JsonUtils;
import org.UEFS.shared.dto.ItinerarioDTO;
import org.UEFS.shared.dto.PassoItinerarioDTO;
import org.UEFS.shared.dto.Trecho;
import org.UEFS.shared.dto.consorcio.TrechoReserva;
import org.UEFS.shared.dto.consorcio.TrechoSolicitado;
import org.UEFS.shared.dto.responses.ReservaResponse;
import org.UEFS.shared.enums.StatusCarona;
import org.UEFS.shared.enums.StatusReserva;
import org.UEFS.shared.model.ServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.UEFS.Server.http.service.ConsorcioUtils.isVazio;

public class TransacaoService {
    private static final Logger log = LoggerFactory.getLogger(TransacaoService.class);
    private final String meuId;
    private final TransacaoRepo repo;
    private final CaronaRepo caronaRepo;
    private final CaronaService caronaService;
    private final PeerClient client = ControllerService.getInstance().get(PeerClient.class);

    public TransacaoService(String meuId, TransacaoRepo repo, CaronaRepo caronaRepo, CaronaService caronaService) {
        this.meuId = meuId;
        this.repo = repo;
        this.caronaRepo = caronaRepo;
        this.caronaService = caronaService;
    }

    public Transacao buscar(String idTransacao) {
        return repo.getByID(idTransacao);
    }
    public List<Transacao> listarTodas() {
        return repo.getDadosList();
    }

    /**
     * Instancia o objeto Transacao para o servidor coordenador.
     * Mapeia os trechos locais solicitados para instâncias de TrechoReserva e
     * define o coordenador e o remetente anterior com o ID deste servidor.
     * @param idTransacao ID único gerado (UUID)
     * @param idPassageiro meuId (como este servidor recebeu o pedido, é o coordenador)
     * @param timestamp Timestamp do Relógio de Lamport
     * @param trechosLocais Lista de trechos pertencentes a este servidor
     * @param proximo  ID do próximo nó
     * @return nova {@link Transacao}
     */
    public Transacao criarTransacaoLocal(String idTransacao, String idPassageiro, long timestamp,
                                         List<TrechoSolicitado> trechosLocais, Vizinho proximo) {

        // Converte os trechos solicitados na estrutura de reserva local (com assento 0 temporário)
        List<TrechoReserva> meusTrechosReserva = new ArrayList<>();
        for (TrechoSolicitado T : trechosLocais) {
            meusTrechosReserva.add(new TrechoReserva(T.servidor(), T.idCarona(), T.origem(), T.destino(), 0));
        }

        // Determina o ID do próximo nó da cadeia (ou string vazia se for o último)
        String proximoId = (proximo != null) ? proximo.id() : "";

        return new Transacao( idTransacao, meuId, meuId, // meuId (como este servidor recebeu o pedido, é o coordenador)
                proximoId, idPassageiro, timestamp, meusTrechosReserva
        );
    }


    /**
     * Fase 1 do 2PC, tenta bloquear os assentos nas caronas lcais.
     * @return Sucesso (OK) ou erro (ex. SEM_VAGA), impedindo condições de corrida.
     */
    public Preparo preparar(Transacao pedido) {
        List<Perna> pernas = new ArrayList<>();

        for (TrechoReserva T : pedido.getLocais()) {
            Perna P = resolver(T);
            if (P == null) return recusa(Resultado.NAO_ENCONTRADO, "carona " + T.idCarona() + " nao existe em " + meuId);
            pernas.add(P);
        }

        synchronized (caronaService) {
            Transacao existente = repo.getByID(pedido.getId());
            if (existente != null) {
                if (!mesmaSolicitacao(existente, pedido))
                    return recusa(Resultado.CONFLITO, "ID de transacao reutilizado com dados diferentes.");

                return switch (existente.getEstado()) {
                    case ABORTADO -> recusa(Resultado.CONFLITO, "transação já foi abortada.");
                    case PREPARADO, COMITADO -> new Preparo(Resultado.OK, existente.getLocais(), true, null);
                    default -> recusa(Resultado.CONFLITO, "transação não pode ser preparada novamente.");
                };
            }

            List<TrechoReserva> assentos = new ArrayList<>();
            List<Perna> ocupadas = new ArrayList<>();
            for (Perna P : pernas) {
                Carona.Ocupacao O = P.carona().ocuparPerna(pedido.getIdPassageiro(), P.trechos());
                if (O.resultado() != Carona.ResultadoOcupacao.OK) {
                    liberar(ocupadas, pedido.getIdPassageiro());
                    return recusa(statusDe(O.resultado()), motivoDe(O.resultado(), P.idPerna()));
                }

                ocupadas.add(P);
                assentos.add(new TrechoReserva(P.servidor(), P.idPerna(),
                        P.trechos().getFirst().origem(), P.trechos().getLast().destino(),
                        O.assento()));
            }

            Transacao preparada = pedido.comAssentos(assentos);
            repo.salvar(preparada);
            if (!repo.salvarNoDisco() || !caronaRepo.salvarNoDisco()) {
                repo.remover(preparada.getId());
                liberar(ocupadas, pedido.getIdPassageiro());
                repo.salvarNoDisco();
                caronaRepo.salvarNoDisco();
                return recusa(Resultado.ERRO, "falha ao gravar reserva em disco (" + meuId + ")");
            }
            return new Preparo(Resultado.OK, assentos, false, null);
        }
    }

    /**
    * Decidir grava o estado final da transacao em disco (Comitado ou cancelado)
    * e o ponto de não retorno para a persistência local, antes do repasse à rede.
    */
    public Resultado decidir(String idTransacao, EstadoTransacao novoEstado) {
        return decidir(idTransacao, novoEstado, null);
    }

    /**
     * Decidir grava o estado final da transacao em disco (Comitado ou cancelado)
     * e o ponto de não retorno para a persistência local, antes do repasse à rede.
     */
    public Resultado decidir(String idTransacao, EstadoTransacao novoEstado, List<TrechoReserva> itinerarioCompleto) {
        synchronized (caronaService) {
            Transacao transacao = repo.getByID(idTransacao);
            if (transacao == null) return Resultado.NAO_ENCONTRADO;

            if (novoEstado != EstadoTransacao.COMITADO) return Resultado.CONFLITO;

            EstadoTransacao estadoAtual = transacao.getEstado();

            // Repetir o commit já persistido
            if (estadoAtual == EstadoTransacao.COMITADO)
                return Resultado.OK;

            // Não permitir commit de transações canceladas ou em andamento
            if (estadoAtual != EstadoTransacao.PREPARADO) {
                log.warn("[TRANSACAO] Commit recusado: id='{}', estado atual='{}', estado solicitado='{}'",
                        idTransacao, estadoAtual, novoEstado
                );

                return Resultado.CONFLITO;
            }

            // O coordenador verifica novamente suas caronas antes de confirmar.
            // Os participantes respeitam o COMMIT recebido após terem votado READY.
            if (itinerarioCompleto != null) {
                if (transacao.isCancelamentoMotoristaPendente()) {
                    log.warn("[TRANSACAO] Commit recusado: cancelamento de motorista pendente. ID='{}'",
                            idTransacao);
                    return Resultado.CONFLITO;
                }

                List<Perna> pernas = pernasDe(transacao);

                if (pernas.size() != transacao.getLocais().size()) {
                    log.warn("[TRANSACAO] Commit recusado: carona local indisponível. ID='{}'",
                            idTransacao);
                    return Resultado.CONFLITO;
                }
            }

            // Backup
            List<TrechoReserva> itinerarioAnterior = transacao.getItinerario();
//            List<Perna> pernas = pernasDe(transacao);
//
//            if (pernas.size() != transacao.getLocais().size()) {
//                log.warn("[TRANSACAO] Commit recusado: uma ou mais caronas não estão disponíveis. ID='{}'",
//                        idTransacao);
//                return Resultado.CONFLITO;
//            }

            // Atualiza o estado
            transacao.setEstado(EstadoTransacao.COMITADO);
            if (itinerarioCompleto != null)
                transacao.setItinerario(itinerarioCompleto);

            // Salva no disco
            repo.salvar(transacao);
            if (!repo.salvarNoDisco()) { // Se falhou, volta o backup
                transacao.setEstado(EstadoTransacao.PREPARADO);
                transacao.setItinerario(itinerarioAnterior);
                return Resultado.ERRO;
            }

            return Resultado.OK;
        }
    }

    /** Chamado no fim do prepare: a recuperação (passo 5) já pode olhar para esta transação. */
    public void terminarProcessamento(String idTransacao) {
        synchronized (caronaService) {
            Transacao t = repo.getByID(idTransacao);
            if (t != null) t.setEmProcessamento(false);
        }
    }

    /**
     * Acionado se a fase 1 falhar num vizinho. Libera as vagas que
     * tinham sido trancadas pelo {@code preparar}.
     */
    public Resultado desfazer(String idTransacao) {
        synchronized (caronaService) {
            Transacao tx = repo.getByID(idTransacao);
            if (tx == null) return Resultado.OK;

            // Rollback repetido
            if (tx.getEstado() == EstadoTransacao.ABORTADO) return Resultado.OK;

            // Uma transação confirmada não pode sofrer rollback
            if (tx.getEstado() != EstadoTransacao.PREPARADO) return Resultado.CONFLITO;

            List<Perna> pernas = pernasDe(tx, true);
            tx.setEstado(EstadoTransacao.ABORTADO);
            liberar(pernas, tx.getIdPassageiro());

            if (!caronaRepo.salvarNoDisco() || !repo.salvarNoDisco()) {
                tx.setEstado(EstadoTransacao.PREPARADO);
                reocupar(pernas, tx.getIdPassageiro());

                caronaRepo.salvarNoDisco();
                repo.salvarNoDisco();

                return Resultado.ERRO;
            }

            return Resultado.OK;
        }
    }

    public Resultado concluirCancelamentoPassageiro(String idTransacao) {
        synchronized (caronaService) {
            Transacao tx = repo.getByID(idTransacao);

            if (tx == null) return Resultado.OK;

            List<Perna> pernas = pernasDe(tx);

            liberar(pernas, tx.getIdPassageiro());

            if (!caronaRepo.salvarNoDisco()) {
                reocupar(pernas, tx.getIdPassageiro());
                return Resultado.ERRO;
            }

            repo.remover(idTransacao);

            if (!repo.salvarNoDisco()) {
                repo.salvar(tx);
                reocupar(pernas, tx.getIdPassageiro());

                caronaRepo.salvarNoDisco();
                return Resultado.ERRO;
            }

            return Resultado.OK;
        }
    }

    /** Ao iniciar: garante que toda transação guardada ainda segura as suas vagas (o caronas.json pode estar mais velho). */
    public void reaplicarOcupacao() {
        synchronized (caronaService) {
            for (Transacao t : repo.getDadosList()) {
                if (t.getEstado() == EstadoTransacao.PREPARADO ||
                    t.getEstado() == EstadoTransacao.COMITADO) {

                    reocupar(pernasDe(t), t.getIdPassageiro());
                }
            }
        }
    }

    /**
     * Consulta as reservas de um passageiro a partir do histórico de transações do servidor,
     * atualizado o estado da transação no processo.
     * @param idPassageiro ID do passageiro para procurar as reservas.
     * @return Lista de {@link ReservaResponse} contendo os dados de todas as reservas
     * encontradas.
     */
    public List<ReservaResponse> consultarReservas(String idPassageiro) {
        List<ReservaResponse> minhasReservas = new ArrayList<>();

        log.info("[RESERVA] Consultando reservas de '{}'", idPassageiro);
        for (Transacao tx : repo.getData()) {
            if (!idPassageiro.equals(tx.getIdPassageiro())) continue;

            // LAZY check para sincronizar o estado caso o coordenador seja outro servidor.
            if (tx.getEstado() == EstadoTransacao.COMITADO &&
                    !tx.getCoordenadorId().equalsIgnoreCase(this.meuId)) {
                sincronizarStatus(tx);
            }

            StatusReserva statusFront = switch (tx.getEstado()) {
                case COMITADO -> StatusReserva.ATIVA;
                case CANCELANDO, CANCELADO_PELO_MOTORISTA, CANCELADO_PELO_PASSAGEIRO -> StatusReserva.CANCELADA;
                default -> null;
            };

            if (statusFront == null) continue;
            List<TrechoReserva> paraExibir = (tx.getItinerario() != null && !tx.getItinerario().isEmpty())
                    ? tx.getItinerario()
                    : tx.getLocais();

            List<PassoItinerarioDTO> passos = makePassosItinerario(paraExibir);

            ItinerarioDTO itinerarioDTO = new ItinerarioDTO(passos);
            minhasReservas.add(new ReservaResponse(tx.getId(), itinerarioDTO, statusFront));
        }
        return minhasReservas;
    }

    /**
     * AÇÃO DO PASSAGEIRO: Muda o estado da transação para cancelada, libera as
     * vagas e inicia a propagação de aviso (Push) para a rede.
     * @param idTransacao ID da transação que se deseja alterar.
     * @param userID ID do usuário Passageiro envolvido na transação.
     */
    public boolean cancelarReserva(String idTransacao, String userID) {
        Transacao tx;
        synchronized (caronaService) {
            tx = repo.getByID(idTransacao);

            if (tx == null) throw new RecursoNaoEncontradoException("Reserva não encontrada.");
            if (!tx.getIdPassageiro().equalsIgnoreCase(userID)) {
                throw new ForbiddenException("Reserva não pertence a esse usuário.");
            }

            if (tx.getEstado() != EstadoTransacao.COMITADO) return false;

            if (tx.souDono(meuId)) {
                throw new ServerException(400, "O cancelamento deve ser iniciado pelo coordenador");
            }

            tx.setEstado(EstadoTransacao.CANCELANDO);
            repo.salvar(tx);

            if (!repo.salvarNoDisco()) {
                tx.setEstado(EstadoTransacao.COMITADO);
                repo.salvarNoDisco();
                return false;
            }
        }

        if (!isVazio(tx.getProximo())) {
            Vizinho proximo = ServerConstant.getVizinho(tx.getProximo());
            if (proximo == null || !client.cancel(proximo, idTransacao, EstadoTransacao.CANCELADO_PELO_PASSAGEIRO))
                return false;
        }

        return concluirCancelamentoPassageiro(idTransacao) == Resultado.OK;
    }

    public boolean registrarCancelamentoMotorista(String idTransacao, String remetente) {
        synchronized (caronaService) {
            Transacao tx = repo.getByID(idTransacao);

            if (tx == null) return false;

            EstadoTransacao estadoAtual = tx.getEstado();

            if (estadoAtual != EstadoTransacao.COMITADO
                    && estadoAtual != EstadoTransacao.CANCELANDO
                    && estadoAtual != EstadoTransacao.CANCELADO_PELO_MOTORISTA) {
                return false;
            }

            if (estadoAtual != EstadoTransacao.CANCELADO_PELO_MOTORISTA) {
                List<Perna> pernas = pernasDe(tx, true);

                boolean avisoAnteriorAnterior = tx.isAvisoAnteriorPendente();
                boolean avisoProximoAnterior = tx.isAvisoProximoPendente();
                boolean pendenciaAnterior = tx.isCancelamentoMotoristaPendente();

                tx.setEstado(EstadoTransacao.CANCELADO_PELO_MOTORISTA);

                if (!pendenciaAnterior) {
                    tx.setAvisoAnteriorPendente(
                            !isVazio(tx.getAnterior()) && !tx.getAnterior().equals(remetente));

                    tx.setAvisoProximoPendente(
                            !isVazio(tx.getProximo()) && !tx.getProximo().equals(remetente));
                }

                liberar(pernas, tx.getIdPassageiro());

                if (!caronaRepo.salvarNoDisco()) {
                    reocupar(pernas, tx.getIdPassageiro());
                    tx.setEstado(estadoAtual);
                    tx.setAvisoAnteriorPendente(avisoAnteriorAnterior);
                    tx.setAvisoProximoPendente(avisoProximoAnterior);
                    return false;
                }

                tx.setCancelamentoMotoristaPendente(false);
                repo.salvar(tx);

                if (!repo.salvarNoDisco()) {
                    reocupar(pernas, tx.getIdPassageiro());
                    tx.setEstado(estadoAtual);
                    tx.setAvisoAnteriorPendente(avisoAnteriorAnterior);
                    tx.setAvisoProximoPendente(avisoProximoAnterior);
                    tx.setCancelamentoMotoristaPendente(pendenciaAnterior);

                    caronaRepo.salvarNoDisco();
                    repo.salvar(tx);
                    repo.salvarNoDisco();

                    return false;
                }
            }

            if (tx.souDono(meuId)) {
                List<TrechoReserva> trechos =
                        tx.getItinerario() != null && !tx.getItinerario().isEmpty()
                                ? tx.getItinerario()
                                : tx.getLocais();

                for (TrechoReserva trecho : trechos) {
                    String servidorId = trecho.servidor();

                    if (servidorId != null
                            && !servidorId.isBlank()
                            && !servidorId.equalsIgnoreCase(meuId)
                            && !servidorId.equalsIgnoreCase(remetente)) {
                        tx.adicionarCancelamentoPendente(servidorId);
                    }
                }
            }

            repo.salvar(tx);
            return repo.salvarNoDisco();
        }
    }

    /**
     * Faz uma requisição ao coordenador da Transação para verificar se
     * houve alguma alteração na situação dela.
     * @param tx {@link Transacao} com coordenador externo para verificação
     */
    private void sincronizarStatus(Transacao tx) {
        Vizinho coordenador = ServerConstant.getVizinho(tx.getCoordenadorId());
        if (coordenador == null) return;

        PeerClient.ResultadoStatus resposta = client.consultarStatusTransacao(coordenador, tx.getId());

        if (resposta.falhaRede()) {
            log.warn("[SYNC] Não foi possível consultar o coordenador '{}'", coordenador.id());
            return;
        }

        if (resposta.inexistente()) {
            log.warn("[SYNC] Transação '{}' não encontrada no coordenador '{}'",
                    tx.getId(), coordenador.id());
            return;
        }

        if (!resposta.conhecido()) {
            log.warn("[SYNC] Coordenador '{}' respondeu HTTP {}",
                    coordenador.id(), resposta.http());
            return;
        }

        log.info("[SYNC] Estado de transação: {}", resposta.corpo());

        try {
            JsonNode json = JsonUtils.getMapper().readTree(resposta.corpo());

            String statusTexto = json.path("status").asText(null);
            if (statusTexto == null) {
                log.warn("[SYNC] Resposta sem campo status: {}", resposta.corpo());
                return;
            }

            EstadoTransacao estadoR = EstadoTransacao.valueOf(statusTexto);
            log.info("[SYNC] Transação '{}': estado remoto = {}", tx.getId(), estadoR);

            switch (estadoR) {
                case CANCELADO_PELO_MOTORISTA, CANCELADO_PELO_PASSAGEIRO -> {
                    tx.setEstado(estadoR);

                    liberar(pernasDe(tx), tx.getIdPassageiro());

                    if (!repo.salvarNoDisco() || !caronaRepo.salvarNoDisco()) {
                        log.error("[SYNC] Falha ao persistir cancelamento da transação '{}'", tx.getId());
                    }
                }

                case COMITADO, CANCELANDO -> { }
                case PREPARADO -> log.warn("[SYNC] Coordenador informa PREPARADO para transação '{}'", tx.getId());
            }

        } catch (JsonProcessingException e) {
            log.error("[SYNC] Resposta de status inválida para '{}': {}",
                    tx.getId(), resposta.corpo(), e);
        }
    }

    /**
     * AÇÃO DO MOTORISTA: Varre todas as reservas penduradas na carona
     * cancelada, liberta as vagas e avisa a rede.
      * @param idCarona ID da carona a ser cancelada.
     */
    public void cancelarTransacoesPorCarona(String idCarona) {
        synchronized (caronaService) {
            List<Transacao> transacoesParaNotificar = new ArrayList<>();

            for (Transacao tx : repo.getData()) {
                boolean envolveEstaCarona = tx.getLocais().stream()
                        .anyMatch(local -> IdPerna.idBase(local.idCarona()).equals(idCarona));

                if (!envolveEstaCarona) continue;

                if (tx.getEstado() == EstadoTransacao.PREPARADO) {
                    tx.setCancelamentoMotoristaPendente(true);

                    log.info("[CANCELAR] Cancelamento de motorista pendente na transação '{}'.",
                            tx.getId());

                    continue;
                }

                if (tx.getEstado() != EstadoTransacao.COMITADO) continue;

                /* O coordenador registra cada servidor do itinerário em cancelamentosPendentes.
                O participante registra os dois vizinhos usando as flags existentes. */
                if (envolveEstaCarona) {
                    tx.setEstado(EstadoTransacao.CANCELADO_PELO_MOTORISTA);

                    if (tx.souDono(meuId)) {
                        List<TrechoReserva> trechos = tx.getItinerario() != null && !tx.getItinerario().isEmpty()
                                        ? tx.getItinerario()
                                        : tx.getLocais();

                        for (TrechoReserva trecho : trechos) {
                            String servidorId = trecho.servidor();

                            if (servidorId != null
                                    && !servidorId.isBlank()
                                    && !servidorId.equalsIgnoreCase(meuId)) {
                                tx.adicionarCancelamentoPendente(servidorId);
                            }
                        }
                    } else {
                        tx.setAvisoAnteriorPendente(!isVazio(tx.getAnterior()));
                        tx.setAvisoProximoPendente(!isVazio(tx.getProximo()));
                    }

                    List<Perna> pernasLocais = pernasDe(tx, true);
                    liberar(pernasLocais, tx.getIdPassageiro());

                    transacoesParaNotificar.add(tx);

                    log.info("[CANCELAR] Transação '{}' cancelada devido a cancelamento da carona '{}'", tx.getId(), idCarona);
                }
            }
            boolean transacoesSalvas = repo.salvarNoDisco();
            boolean caronasSalvas = caronaRepo.salvarNoDisco();

            if (!transacoesSalvas || !caronasSalvas) {
                log.error("[TRANSAÇÕES] !! Erro ao salvar no disco: Transações: {}, Caronas: {}", transacoesSalvas, caronasSalvas);
                return;
            }

            transacoesParaNotificar.forEach(this::notificarCancelamento);
        }
    }

    /**
     * Retenta a propagação de um cancelamento feito pelo passageiro.
     * O coordenador utiliza o itinerário completo para reenviar o aviso
     * aos demais servidores. Como o cancelamento remoto é idempotente,
     * é seguro reenviar mensagens já recebidas.
     *
     * @return true se todos os servidores foram notificados.
     */
    public boolean retentarCancelamentoPassageiro(Transacao tx) {
        if (tx == null || tx.getEstado() != EstadoTransacao.CANCELADO_PELO_PASSAGEIRO)
            return true;

        List<TrechoReserva> trechos =
                tx.getItinerario() != null && !tx.getItinerario().isEmpty()
                        ? tx.getItinerario()
                        : tx.getLocais();

        Set<String> servidores = new HashSet<>();
        boolean sucesso = true;

        for (TrechoReserva trecho : trechos) {
            String servidorId = trecho.servidor();

            if (servidorId.equalsIgnoreCase(meuId) || !servidores.add(servidorId))
                continue;

            Vizinho vizinho = ServerConstant.getVizinho(servidorId);

            if (vizinho == null || !client.cancel(vizinho, tx.getId(), tx.getEstado())) {
                sucesso = false;
                log.warn("[RECUPERAÇÃO] Falha ao propagar cancelamento da transação '{}' para '{}'.",
                        tx.getId(), servidorId);
            }
        }

        return sucesso;
    }

    /**
     * PUSH (Assíncrono). </div>
     * Dispara requisições HTTP em segundo plano para avisar o Coordenador
     * e/ou os vizinhos sobre o cancelamento.
     * @param tx {@link Transacao} modificada.
     */
    private void notificarCancelamento(Transacao tx) {
        notificarCancelamento(tx, null);
    }

    private void notificarCancelamento(Transacao tx, String remetente) {
        CompletableFuture.runAsync(() -> {
            if (meuId.equals(tx.getCoordenadorId())) {
                Set<String> servidoresNotify = new HashSet<>();

                List<TrechoReserva> trechos = (tx.getItinerario() != null && !tx.getItinerario().isEmpty())
                                ? tx.getItinerario()
                                : tx.getLocais();

                for (TrechoReserva tr : trechos) {
                    String idServidor = tr.servidor();

                    if (idServidor == null
                            || idServidor.isBlank()
                            || idServidor.equalsIgnoreCase(meuId)
                            || (remetente != null && idServidor.equalsIgnoreCase(remetente))
                            || !servidoresNotify.add(idServidor)) {
                        continue;
                    }

                    Vizinho vizinho = ServerConstant.getVizinho(idServidor);

                    if (vizinho != null && client.cancel(vizinho, tx.getId(), tx.getEstado())) {
                        tx.confirmarCancelamento(idServidor);
                    }
                }
            } else {
                String anterior = tx.getAnterior();

                if (anterior != null
                        && !anterior.isEmpty()
                        && !anterior.equalsIgnoreCase(remetente)) {

                    Vizinho vizinho = ServerConstant.getVizinho(anterior);

                    if (vizinho != null && client.cancel(vizinho, tx.getId(), tx.getEstado())) {
                            tx.setAvisoAnteriorPendente(false);
                    }
                }

                String proximo = tx.getProximo();

                if (proximo != null
                        && !proximo.isEmpty()
                        && !proximo.equalsIgnoreCase(remetente)) {

                    Vizinho vizinho = ServerConstant.getVizinho(proximo);

                    if (vizinho != null && client.cancel(vizinho, tx.getId(), tx.getEstado())) {
                        tx.setAvisoProximoPendente(false);
                    }
                }
            }

            repo.salvar(tx);
            repo.salvarNoDisco();
        });
    }

    /**
     * P2P: Recebe um cancelamento vindo da rede, processa localmente
     * e continua a propagar a onda.
     */
    public boolean receberCancelamentoDaRede(String idTransacao, EstadoTransacao estado, String remetente) {
        if (estado != EstadoTransacao.CANCELADO_PELO_PASSAGEIRO
                && estado != EstadoTransacao.CANCELADO_PELO_MOTORISTA) {
            throw new IllegalArgumentException("Estado de cancelamento inválido.");
        }

        synchronized (caronaService) {
            Transacao tx = repo.getByID(idTransacao);

            if (tx == null) {
                log.warn("[REDE] Transação '{}' não encontrada.", idTransacao);
                return true;
            }

            EstadoTransacao estadoAtual = tx.getEstado();
            if (estado == EstadoTransacao.CANCELADO_PELO_MOTORISTA
                    && estadoAtual == EstadoTransacao.PREPARADO) {

                tx.setCancelamentoMotoristaPendente(true);
                tx.setAvisoAnteriorPendente(!isVazio(tx.getAnterior()) && !tx.getAnterior().equals(remetente));
                tx.setAvisoProximoPendente(!isVazio(tx.getProximo()) && !tx.getProximo().equals(remetente));

                repo.salvar(tx);

                if (!repo.salvarNoDisco()) {
                    tx.setCancelamentoMotoristaPendente(false);
                    tx.setAvisoAnteriorPendente(false);
                    tx.setAvisoProximoPendente(false);
                    repo.salvar(tx);
                    repo.salvarNoDisco();
                    return false;
                }

                log.info("[REDE] Cancelamento de motorista pendente na transação '{}'.", idTransacao);
                return true;
            }

            // Cancelamento pelo motorista tem prioridade.
            if (estadoAtual == EstadoTransacao.CANCELADO_PELO_MOTORISTA) {
                notificarCancelamento(tx, remetente);
                return true;
            }

            EstadoTransacao novoEstado =
                    estado == EstadoTransacao.CANCELADO_PELO_MOTORISTA
                            ? EstadoTransacao.CANCELADO_PELO_MOTORISTA
                            : EstadoTransacao.CANCELADO_PELO_PASSAGEIRO;

            // Evita processar novamente o mesmo cancelamento.
            if (estadoAtual == novoEstado) {
                return true;
            }

            boolean jaEstavaCancelada =
                    estadoAtual == EstadoTransacao.CANCELADO_PELO_PASSAGEIRO;

            log.info("[REDE] Cancelamento recebido: transação={}, estado={}, remetente={}",
                    idTransacao, novoEstado, remetente);

            tx.setEstado(novoEstado);
            repo.salvar(tx);
            // Tenta salvar, se der errado tenta recuperar o estado anterior
            if (!repo.salvarNoDisco()) {
                tx.setEstado(estadoAtual);
                repo.salvar(tx);
                repo.salvarNoDisco();

                return false;
            }

            // Só libera os assentos na primeira transição para cancelada.
            if (!jaEstavaCancelada) {
                List<Perna> pernas = pernasDe(tx);
                liberar(pernas, tx.getIdPassageiro());

                if (!caronaRepo.salvarNoDisco()) {
                    reocupar(pernas, tx.getIdPassageiro());

                    tx.setEstado(estadoAtual);
                    repo.salvar(tx);

                    repo.salvarNoDisco();
                    caronaRepo.salvarNoDisco();

                    return false;
                }
            }
            // Continua a propagação, sem retornar ao remetente.
            notificarCancelamento(tx, remetente);
            return true;
        }
    }

    // ======================= UTILS
    private Perna resolver(TrechoReserva T) {
        return resolver(T, false);
    }

    private Perna resolver(TrechoReserva T, Boolean permitirCancelada) {
        Optional<IdPerna> id = IdPerna.decodificar(T.idCarona());

        String idBase = IdPerna.idBase(T.idCarona());

        Carona carona = caronaRepo.getByID(idBase);

        if (carona == null) return null;

        if (!permitirCancelada && carona.getStatus() == StatusCarona.CANCELADA)
            return null;

        List<Trecho> trechos = id.isPresent()
                ? carona.getRota().subRota(id.get().inicio(), id.get().fim())
                : carona.getRota().getTrechos();

        return trechos.isEmpty()
                ? null
                : new Perna(T.servidor(), T.idCarona(), carona, List.copyOf(trechos));
    }

    private List<Perna> pernasDe(Transacao T) {
        return pernasDe(T, false);
    }

    private List<Perna> pernasDe(Transacao T, boolean permitirCancelada) {
        List<Perna> pernas = new ArrayList<>();

        for (TrechoReserva X : T.getLocais()) {
            Perna P = resolver(X, permitirCancelada);

            if (P != null) pernas.add(P);
        }

        return pernas;
    }

    private boolean mesmaSolicitacao(Transacao existente, Transacao pedido) {
        if (!Objects.equals(existente.getCoordenadorId(), pedido.getCoordenadorId())
                || !Objects.equals(existente.getAnterior(), pedido.getAnterior())
                || !Objects.equals(existente.getProximo(), pedido.getProximo())
                || !Objects.equals(existente.getIdPassageiro(), pedido.getIdPassageiro())
                || existente.getRelogio() != pedido.getRelogio()) {
            return false;
        }

        List<TrechoReserva> A = existente.getLocais();
        List<TrechoReserva> B = pedido.getLocais();

        if (A.size() != B.size()) return false;

        for (int i = 0; i < A.size(); i++) {
            TrechoReserva a = A.get(i);
            TrechoReserva b = B.get(i);

            if (!Objects.equals(a.servidor(), b.servidor())
                    || !Objects.equals(a.idCarona(), b.idCarona())
                    || a.origem() != b.origem()
                    || a.destino() != b.destino()) {
                return false;
            }
        }

        return true;
    }

    private List<PassoItinerarioDTO> makePassosItinerario(List<TrechoReserva> trechos) {
        if (trechos == null) return List.of();
        return trechos.stream()
                .map(trecho -> {
                    // Decodifica o idCarona para extrair a origem e o destino originais da Perna
                    Optional<IdPerna> idPerna = IdPerna.decodificar(trecho.idCarona());
                    int inicio = idPerna.map(IdPerna::inicio).orElse(-1);
                    int fim = idPerna.map(IdPerna::fim).orElse(-1);

                    // Extrai o ID base real (sem os números de origem/destino anexados)
                    String idBaseCarona = IdPerna.idBase(trecho.idCarona());

                    // Busca o motorista (se a carona for remota e não existir no repo local, define um fallback)
                    Carona carona = caronaRepo.getByID(idBaseCarona);
                    String idMotorista = (carona != null) ? carona.getId_motorista() : "externo";

                    return new PassoItinerarioDTO(inicio, fim, idBaseCarona, idMotorista);
                }).toList();
    }

    private void liberar(List<Perna> pernas, String idPassageiro) {
        for (Perna p : pernas) p.carona().liberarPerna(idPassageiro, p.trechos());
    }

    private void reocupar(List<Perna> pernas, String idPassageiro) {
        for (Perna p : pernas) p.carona().ocuparPerna(idPassageiro, p.trechos());
    }

    private Resultado statusDe(Carona.ResultadoOcupacao R) {
        return R == Carona.ResultadoOcupacao.TRECHO_INEXISTENTE ? Resultado.NAO_ENCONTRADO : Resultado.INDISPONIVEL;
    }

    private String motivoDe(Carona.ResultadoOcupacao R, String idPerna) {
        return switch (R) {
            case SEM_VAGA -> "sem assento disponível na carona " + idPerna + " (" + meuId + ")";
            case JA_NO_TRECHO -> "passageiro ja tem vaga na carona " + idPerna + " (" + meuId + ")";
            case CARONA_FECHADA -> "carona " + idPerna + " não está aberta (" + meuId + ")";
            case TRECHO_INEXISTENTE -> "carona " + idPerna + " não existe em " + meuId;
            default -> "falha ao reservar a carona " + idPerna + " (" + meuId + ")";
        };
    }

    private Preparo recusa(Resultado r, String motivo) { return new Preparo(r, List.of(), false, motivo); }

    public void salvar(Transacao tx) {
        repo.salvar(tx);
    }
    public boolean salvarNoDisco() {
        return repo.salvarNoDisco();
    }
}
