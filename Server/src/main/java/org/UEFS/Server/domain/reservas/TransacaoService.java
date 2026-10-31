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
import org.UEFS.shared.enums.StatusReserva;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

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
            meusTrechosReserva.add(new TrechoReserva(T.servidor(), T.idCarona(), 0));
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
                assentos.add(new TrechoReserva(P.servidor(), P.idPerna(), O.assento()));
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
            if (estadoAtual != EstadoTransacao.PREPARADO)
                return Resultado.CONFLITO;

            // Backup
            List<TrechoReserva> itinerarioAnterior = transacao.getItinerario();

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
        Transacao t = repo.getByID(idTransacao);
        if (t != null) t.setEmProcessamento(false);
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

            List<Perna> pernas = pernasDe(tx);
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

    /**
     *  O passo 4 reaproveita com false (cancelamento)
     */
    private Resultado liberarEApagar(String id, boolean soSePreparado) {
        synchronized (caronaService) {
            Transacao t = repo.getByID(id);
            if (t == null) return Resultado.OK;
            if (soSePreparado && t.getEstado() != EstadoTransacao.PREPARADO) return Resultado.CONFLITO;

            List<Perna> pernas = pernasDe(t);
            liberar(pernas, t.getIdPassageiro()); // solta as vagas e grava as caronas...
            if (!caronaRepo.salvarNoDisco()) { reocupar(pernas, t.getIdPassageiro()); return Resultado.ERRO; }
            repo.remover(id); // ... e só então apaga o registro
            if (!repo.salvarNoDisco()) {
                repo.salvar(t);
                reocupar(pernas, t.getIdPassageiro()); // memória volta a ser igual ao disco
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
    public void cancelarReserva(String idTransacao, String userID) {
        synchronized (caronaService) {
            Transacao tx = repo.getByID(idTransacao);

            if (tx == null) throw new RecursoNaoEncontradoException("Reserva não encontrada.");
            if (!tx.getIdPassageiro().equalsIgnoreCase(userID)) {
                throw new ForbiddenException("Reserva não pertence a esse usuário.");
            }

            if (tx.getEstado() != EstadoTransacao.COMITADO) return;

            tx.setEstado(EstadoTransacao.CANCELADO_PELO_PASSAGEIRO);
            repo.salvar(tx);

            List<Perna> pernasLocais = pernasDe(tx);
            liberar(pernasLocais, tx.getIdPassageiro());

            if (!repo.salvarNoDisco() || caronaRepo.salvarNoDisco()) {
                log.error("[ERRO CRÍTICO] Falha ao persistir cancelamento de Reserva.");
            }

            notificarCancelamento(tx);
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
            for (Transacao tx : repo.getData()) {
                if (!tx.comitado()) continue;

                boolean envolveEstaCarona = tx.getLocais().stream()
                        .anyMatch(local -> IdPerna.idBase(local.idCarona()).equals(idCarona));

                if (envolveEstaCarona) {
                    tx.setEstado(EstadoTransacao.CANCELADO_PELO_MOTORISTA);

                    List<Perna> pernasLocais = pernasDe(tx);
                    liberar(pernasLocais, tx.getIdPassageiro());

                    notificarCancelamento(tx);

                    log.info("[CANCELAR] Transação '{}' cancelada devido a cancelamento da carona '{}'", tx.getId(), idCarona);
                }
            }
            repo.salvarNoDisco();
            caronaRepo.salvarNoDisco();
        }
    }

    /**
     * PUSH (Assíncrono). </div>
     * Dispara requisições HTTP em segundo plano para avisar o Coordenador
     * e/ou os vizinhos sobre o cancelamento.
     * @param tx {@link Transacao} modificada.
     */
    private void notificarCancelamento(Transacao tx) {
        CompletableFuture.runAsync(() -> {  // Roda em uma thread secundária.
            if (meuId.equals(tx.getCoordenadorId())) {
                Set<String> servidoresNotify = new HashSet<>();
                List<TrechoReserva> trechos = (tx.getItinerario() != null && !tx.getItinerario().isEmpty())
                        ? tx.getItinerario()
                        : tx.getLocais();

                for (TrechoReserva tr : trechos) {
                    String idServidor = tr.servidor();
                    if (!idServidor.equals(meuId) && servidoresNotify.add(idServidor)) {
                        Vizinho vizinho = ServerConstant.getVizinho(idServidor);
                        if (vizinho != null) client.cancel(vizinho, tx.getId());
                    }
                }
            } else {
                if (tx.getProximo() != null && !tx.getProximo().isEmpty()) {
                    Vizinho vizinhoProximo = ServerConstant.getVizinho(tx.getProximo());
                    if (vizinhoProximo != null) client.cancel(vizinhoProximo, tx.getId());
                }

                if (tx.getCoordenadorId() != null
                        && !tx.getCoordenadorId().isEmpty()
                        && !tx.getCoordenadorId().equals(this.meuId)) {
                    Vizinho vizinhoCoord = ServerConstant.getVizinho(tx.getCoordenadorId());
                    if (vizinhoCoord != null) client.cancel(vizinhoCoord, tx.getId());
                }
            }
        });
    }

    /**
     * P2P: Recebe um cancelamento vindo da rede, processa localmente
     * e continua a propagar a onda.
     */
    public void receberCancelamentoDaRede(String idTransacao) {
        synchronized (caronaService) {
            Transacao tx = repo.getByID(idTransacao);

            // Se a transação não existe ou JÁ ESTÁ cancelada, não faz nada (trava o loop infinito)
            if (tx == null ||
                    tx.getEstado() == EstadoTransacao.CANCELADO_PELO_MOTORISTA ||
                    tx.getEstado() == EstadoTransacao.CANCELADO_PELO_PASSAGEIRO) {
                return;
            }

            log.info("[REDE] Recebido cancelamento remoto para a transação {}", idTransacao);

            // Atualiza o estado
            tx.setEstado(EstadoTransacao.CANCELADO_PELO_MOTORISTA); // Estado genérico para cancelamento em rede
            repo.salvar(tx);

            // Liberta as vagas presas localmente
            List<Perna> pernasLocais = pernasDe(tx);
            liberar(pernasLocais, tx.getIdPassageiro());

            if (!repo.salvarNoDisco() || caronaRepo.salvarNoDisco())
                log.error("[ERRO CRÍTICO] Falha ao persistir cancelamento de transação.");

            // PROPAGA a onda adiante!
            notificarCancelamento(tx);
        }
    }

    // ======================= UTILS
    private Perna resolver(TrechoReserva T) {
        Optional<IdPerna> id = IdPerna.decodificar(T.idCarona());

        String idBase = IdPerna.idBase(T.idCarona());

        log.info("ID recebido: '{}'", T.idCarona());
        log.info("ID base: '{}'", idBase);
        log.info("Carona encontrada: {}", caronaRepo.getByID(idBase) != null);

        Carona carona = caronaRepo.getByID(IdPerna.idBase(T.idCarona()));
        if (carona == null) return null;

        List<Trecho> trechos = id.isPresent()
                ? carona.getRota().subRota(id.get().inicio(), id.get().fim())
                : carona.getRota().getTrechos();

        return trechos.isEmpty() ? null : new Perna(T.servidor(), T.idCarona(), carona, List.copyOf(trechos));
    }

    private List<Perna> pernasDe(Transacao T) {
        List<Perna> pernas = new ArrayList<>();
        for (TrechoReserva X : T.getLocais()) {
            Perna P = resolver(X);
            if (P != null) pernas.add(P);
        }
        return pernas;
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

}
