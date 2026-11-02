package org.UEFS.Server.http.cluster;

import org.UEFS.Server.domain.reservas.EstadoTransacao;
import org.UEFS.Server.http.relogio.RelogioLamport;
import org.UEFS.Server.http.relogio.ServerConstant;
import org.UEFS.Server.http.service.ConsorcioUtils;
import org.UEFS.Server.http.service.Vizinho;
import org.UEFS.shared.JsonUtils;
import org.UEFS.shared.dto.OtherUserDTO;
import org.UEFS.shared.dto.consorcio.BuscaTrechosResponse;
import org.UEFS.shared.dto.consorcio.RespostaPrepare;
import org.UEFS.shared.dto.consorcio.ReservaRequest;
import org.UEFS.shared.model.Regiao;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.UEFS.Server.http.service.ConsorcioUtils.isVazio;

public class PeerClient implements Repasse {
    private static final Logger log = LoggerFactory.getLogger(PeerClient.class);
    private static final long SUSPEITA_MS = 3_000;
    private static final long DURATION_S = 2;
    private static final long TIMEOUT_BUSCA_MS = 2_000;
    private static final long TIMEOUT_REQUISICAO_MS = 1_000;
    private static final long ESPERA_MAX_MS = 2_000;

    private final HttpClient httpClient;
    private final RelogioLamport relogio;
    private final String meuServidorID;

    private final Map<String, Long> suspeitoAte = new ConcurrentHashMap<>();

    public record ResultadoStatus(boolean respondeu, int http, String corpo) {
        public boolean conhecido() {
            return respondeu && http == 200;
        }
        public boolean inexistente() {
            return respondeu && http == 404;
        }
        public boolean falhaRede() {
            return !respondeu;
        }
    }

    public PeerClient(String meuServidorID, RelogioLamport relogio) {
        this.meuServidorID = meuServidorID;
        this.relogio = relogio;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /**
     * Busca rotas disponíveis nos vizinhos passando um histórico de
     * {@code visitados} para evitar recursões infinitas (<b>backtracking</b>).
     * @param vizinho Próximo servidor na cadeia.
     * @param origem Cidade de origem.
     * @param destino Cidade de destino.
     * @param visitadosCsv Cidades visitadas.
     * @return Um {@link Optional} contendo a lista de itinerários ou vazio.
     */
    public Optional<BuscaTrechosResponse> buscaTrechosRemotos(Vizinho vizinho, String origem, String destino, String visitadosCsv) {
        try {
            String url = "/api/consorcio/trechos"
                    + "?origem=" + codificar(origem)
                    + "&destino=" + codificar(destino)
                    + "&visitados=" + codificar(visitadosCsv);

            Resposta r = enviar(vizinho, "GET", url, null, Duration.ofMillis(TIMEOUT_BUSCA_MS));

            if (r.redeFalhou()) return Optional.empty();
            if (r.status() != 200) {
                log.warn("[REDE] {} respondeu HTTP {}", vizinho.id(), r.status());
                return Optional.empty();
            }

            try { return Optional.of(JsonUtils.fromJson(r.corpo, BuscaTrechosResponse.class));
            } catch (RuntimeException e) {
                log.error("[REDE] resposta inválida de {}: {}", vizinho.id(), e.getMessage());
                return Optional.empty();
            }
        } catch (RuntimeException e) {                              // resposta que não é o JSON esperado
            log.error("[REDE] resposta invalida de {}: {}", vizinho.id(), e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Requisição rápida usada pelo Lazy Check para descobrir se o estado remoto mudou.
     * @param coordenador Vizinho que foi o coordenador da transação.
     * @param idTransacao Id da transação.
     * @return Estado atual da transação.
     */
    public ResultadoStatus consultarStatusTransacao(Vizinho coordenador, String idTransacao) {
        Duration timeout = Duration.ofSeconds(DURATION_S);
        String caminho = "/api/consorcio/reservas/"+ idTransacao +"/status";

        Resposta r = enviar(coordenador, "GET", caminho, null, timeout);

        if (r.redeFalhou()) return new ResultadoStatus(false, 0, null);

        return new ResultadoStatus(true, r.status(), r.corpo());
    }

    // RESERVA (repasse) ======================
    @Override
    public ResultadoRepasse prepare(Vizinho proximo, ReservaRequest pedidoRestante, int servidoresRestantes) {
        Duration timeout = Duration.ofMillis((TIMEOUT_REQUISICAO_MS + ESPERA_MAX_MS) * Math.max(1, servidoresRestantes));
        Resposta r = enviar(
                proximo, "POST", "/api/consorcio/reservas/prepare",
                JsonUtils.toJson(pedidoRestante), timeout
        );
        if (r.redeFalhou()) {
            return ResultadoRepasse.rede("Servidor: "+ proximo.id() + " indisponível (" + r.erro() +")");
        }

        try {
            RespostaPrepare resposta = JsonUtils.fromJson(r.corpo(), RespostaPrepare.class);
            if (r.status() == 200)
                return ResultadoRepasse.sucesso(resposta.assentos() == null ? List.of() : resposta.assentos());

            String motivo = resposta.motivo() != null
                           ? resposta.motivo()
                           : resposta.erro() != null
                           ? resposta.erro()
                           : "Recusado por " + proximo.id();

            int http = (r.status() == 404 || r.status() == 409) ? r.status() : 502;
            return ResultadoRepasse.recusa(http, motivo);
        } catch (RuntimeException e) {
            return ResultadoRepasse.rede("Resposta inválida de " + proximo.id());
        }
    }

    @Override
    public boolean rollback(Vizinho proximo, String idTransacao) {
        Resposta res = enviar(proximo, "DELETE", "/api/consorcio/reservas/" + idTransacao,
                null, Duration.ofSeconds(DURATION_S));

        return !res.redeFalhou() && res.status() == 200;
    }

    @Override
    public boolean commit(Vizinho proximo, String idTransacao) {
        Resposta R = enviar(
                proximo, "PUT",
                "/api/consorcio/reservas/" + idTransacao + "/commit",
                null, Duration.ofSeconds(DURATION_S)
        );
        return !R.redeFalhou() && R.status() == 200;
    }

    @Override
    public boolean cancel(Vizinho vizinho, String idTransacao, EstadoTransacao estado) {
        String corpo = JsonUtils.toJson(Map.of("estado", estado.name()));

        Resposta resposta = enviar(
                vizinho,
                "PUT",
                "/api/consorcio/reservas/" + idTransacao + "/cancel",
                corpo,
                Duration.ofSeconds(DURATION_S)
        );

        return !resposta.redeFalhou() && resposta.status() == 200;
    }

    public boolean cancelPassageiro(Vizinho proximo, String idTransacao) {
        Resposta r = enviar(
                proximo, "PUT",
                "/api/consorcio/reservas/" + idTransacao + "/cancel/passageiro",
                null, Duration.ofSeconds(DURATION_S)
        );
        return !r.redeFalhou() && r.status() == 200;
    }

    public boolean cancelMotorista(Vizinho vizinho, String idTransacao) {
        Resposta r = enviar(
                vizinho, "PUT",
                "/api/consorcio/reservas/" + idTransacao + "/cancel/motorista",
                null, Duration.ofSeconds(DURATION_S)
        );
        return !r.redeFalhou() && r.status() == 200;
    }

    // UTIL ====================================

    /** O C codifica espaço como %20; o URLEncoder do Java usa '+'. Padronizar evita depender de como o libmicrohttpd trata o '+'. */
    private static String codificar(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void atualizarRelogio(String S) {
        try { relogio.atualizar(Integer.parseInt(S.trim())); }
        catch (NumberFormatException e) {
            log.error("[PEER_CLIENT] !! Erro ao atualizar relógio: {}", e.getMessage());
        }
    }

    public boolean suspeito(String idVizinho) {
        Long ate = suspeitoAte.get(idVizinho);
        return ate != null && ate > System.currentTimeMillis();
    }

    /**
     * Pede os dados de um usuário aos servidores vizinhos, que por sua vez fazem o mesmo
     * caso não tenham encontrado o user no seu banco de dados.
     * @param idUsuario ID do usuário sendo buscado.
     * @param visitadosCsv Servidores que já foram consultados.
     * @return {@link OtherUserDTO} caso tenham encontrado ou {@code null} caso
     * a resposta seja incorreta ou não tenham encontrado.
     */
    public OtherUserDTO buscarDadosUser(String idUsuario, String visitadosCsv) {
        if (idUsuario == null || idUsuario.isBlank()) {
            return null;
        }

        Set<String> visitados = new LinkedHashSet<>();

        if (visitadosCsv != null && !visitadosCsv.isBlank()) {
            for (String id : visitadosCsv.split(",")) {
                visitados.add(ServerConstant.normalizar(id.trim()));
            }
        }

        String meuId = ServerConstant.normalizar(meuServidorID);

        // Impede que o servidor processe novamente uma consulta
        // que já passou por ele.
        if (!visitados.add(meuId)) {
            return null;
        }

        String historico = String.join(",", visitados);

        for (Vizinho vizinho : ServerConstant.getVizinhos()) {
            String idVizinho = ServerConstant.normalizar(vizinho.id());

            if (visitados.contains(idVizinho)) continue;
            if (suspeito(vizinho.id())) continue;

            String caminho = "/api/consorcio/usuarios/"
                    + codificar(idUsuario)
                    + "?visitados=" + codificar(historico);

            Resposta resposta = enviar(vizinho, "GET", caminho, null,
                    Duration.ofMillis(TIMEOUT_BUSCA_MS)
            );

            if (resposta.status() == 200) {
                try { return JsonUtils.fromJson( resposta.corpo(), OtherUserDTO.class );
                } catch (RuntimeException e) {
                    log.error("[BUSCA] Resposta inválida de {}: {}", vizinho.id(), e.getMessage() );
                }
            }
        }

        return null;
    }

    public OtherUserDTO buscarDadosUser(String idPassageiro) {
        return buscarDadosUser(idPassageiro, null);
    }

    private record Resposta(int status, String corpo, boolean redeFalhou, String erro) {}

    /** Uma requisição a um vizinho: relógio, X-Server-Id e marcação de suspeito, como o requisicao() do C. */
    private Resposta enviar(Vizinho vizinho, String metodo, String caminho, String corpo, Duration timeout) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(vizinho.url() + caminho))
                    .timeout(timeout)
                    .header("X-Server-Id", meuServidorID)
                    .header("X-Lamport-Clock", String.valueOf(relogio.getTempo()))
                    .header("Accept", "application/json")
                    .method(metodo, corpo == null
                                                            ? HttpRequest.BodyPublishers.noBody()
                                                            : HttpRequest.BodyPublishers.ofString(corpo));
            if (corpo != null) req.header("Content-Type", "application/json");

            HttpResponse<String> response = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());

            suspeitoAte.remove(vizinho.id());                              // respondeu: está vivo
            response.headers().firstValue("X-Lamport-Clock").ifPresent(this::atualizarRelogio);
            return new Resposta(response.statusCode(), response.body(), false, null);
        } catch (IOException e) {                                          // conexão recusada, timeout, host desconhecido
            suspeitoAte.put(vizinho.id(), System.currentTimeMillis() + SUSPEITA_MS);
            log.error("[REDE] !! falha de comunicação com {}: {}", vizinho.id(), e.getMessage());
            return new Resposta(0, "", true, String.valueOf(e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Resposta(0, "", true, "interrompido");
        }
    }
}
