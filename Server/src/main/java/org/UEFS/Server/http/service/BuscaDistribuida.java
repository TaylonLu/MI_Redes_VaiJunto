package org.UEFS.Server.http.service;

import org.UEFS.Server.controller.ControllerService;
import org.UEFS.Server.domain.caronas.Carona;
import org.UEFS.Server.domain.caronas.CaronaRepo;
import org.UEFS.Server.domain.cidades.CidadeMapper;
import org.UEFS.Server.http.cluster.PeerClient;
import org.UEFS.Server.http.relogio.ServerConstant;
import org.UEFS.shared.dto.Trecho;
import org.UEFS.shared.dto.consorcio.BuscaTrechosResponse;
import org.UEFS.shared.dto.consorcio.ItinerarioConsorcioDTO;
import org.UEFS.shared.dto.consorcio.TrechoConsorcioDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class BuscaDistribuida {
    private static final int MAX_ITINERARIOS = 30;
    private static final int MAX_CONSULTAS = 40;
    private static final int MAX_VISITADOS = 16;
    private static final int MAX_TRECHOS_TX = 8;
    private static final Logger log = LoggerFactory.getLogger(BuscaDistribuida.class);

    private final String meuServidorId;
    private final List<Vizinho> vizinhos;
    private final CaronaRepo caronaRepo;
    private final PeerClient peerClient;
    private final CidadeMapper cidadeMapper = ControllerService.getInstance().get(CidadeMapper.class);

    public BuscaDistribuida(String meuServidorId, List<Vizinho> vizinhos, CaronaRepo caronaRepo, PeerClient peerClient) {
        this.meuServidorId = meuServidorId;
        this.vizinhos = vizinhos;
        this.caronaRepo = caronaRepo;
        this.peerClient = peerClient;
    }

    /**
     * Representa o estado de <b>UMA</b> busca.
     * A classe é compartilhada entre requisições, então nada
     * disso pode ficar em campo.
     */
    private static final class Execucao {
        final int destinoId;
        final String destinoNome;                                  // como veio na requisição (o C compara sem diferenciar maiúsculas)
        final Set<String> visitados = new LinkedHashSet<>();       // ids de SERVIDORES, normalizados
        final Set<Integer> cidadesNoCaminho = new HashSet<>();
        final List<TrechoConsorcioDTO> prefixo = new ArrayList<>();
        final List<ItinerarioConsorcioDTO> resultados = new ArrayList<>();
        int consultas;

        Execucao(int destinoId, String destinoNome) { this.destinoId = destinoId; this.destinoNome = destinoNome; }
    }

    public BuscaTrechosResponse executar(String origemNome, String destinoNome, String visitadosCsv, boolean incluirRemotoOrigem) {
        Integer origem = idDaCidade(origemNome);
        Integer destino = idDaCidade(destinoNome);
        if (origem == null || destino == null || origem == -1 || destino == -1) {
            log.warn("[BUSCA] Abortada: origem ({}) ou destino ({}) inválidos.", origemNome, destinoNome);
            return new BuscaTrechosResponse(meuServidorId, 0, List.of());
        }

        Execucao EX = new Execucao(destino, destinoNome.trim());
        if (visitadosCsv != null) {
            for (String token : visitadosCsv.split(",")) {
                adicionarVisitado(EX, token);
            }
        }

        adicionarVisitado(EX, meuServidorId);
        EX.cidadesNoCaminho.add(origem);

        log.info("[BUSCA] {} -> {} (incluirRemotoOrigem={})", origem, destino, incluirRemotoOrigem);

        buscarLocal(origem, EX);

        if (incluirRemotoOrigem) {
            buscarRemoto(origemNome, EX);
        }

        log.info("[BUSCA] {} itinerario(s), {} consulta(s) a vizinhos", EX.resultados.size(), EX.consultas);
        return new BuscaTrechosResponse(meuServidorId, EX.resultados.size(), EX.resultados);
    }

    // ======================= Algoritmos de Busca

    private void buscarLocal(int origem, Execucao EX) {
        // Limites da busca
        if (EX.resultados.size() >= MAX_ITINERARIOS || EX.prefixo.size() >= MAX_TRECHOS_TX) {
            return;
        }

        List<Carona> caronasLocais = caronaRepo.getDadosList();

        for (Carona C : caronasLocais) {
            if (EX.resultados.size() >= MAX_ITINERARIOS) return;
            if (!C.emAberto()) continue;

            // Uma carona não pode aparecer duas vezes no itinerário.
            boolean jaUtilizada = EX.prefixo.stream()
                    .anyMatch(t -> idBaseBusca(t.idCarona()).equals(C.getId()));

            if (jaUtilizada) continue;

            List<Trecho> trechos = C.getRota().getTrechos();

            // Localiza a posição da cidade atual na rota.
            int inicio = -1;

            for (int i = 0; i < trechos.size(); i++) {
                if (trechos.get(i).origem() == origem) {
                    inicio = i;
                    break;
                }
            }

            if (inicio == -1) continue;

            double precoAcumulado = 0;

            // Experimenta todas as paradas posteriores.
            for (int fim = inicio; fim < trechos.size(); fim++) {
                if (EX.resultados.size() >= MAX_ITINERARIOS ||
                        EX.prefixo.size() >= MAX_TRECHOS_TX) {
                    return;
                }

                Trecho trechoAtual = trechos.get(fim);
                int cidadeDestino = trechoAtual.destino();

                precoAcumulado += C.getValorPorTrecho();

                // Evita ciclos no caminho atual.
                List<Trecho> faixa = trechos.subList(inicio, fim + 1);

                // Evita passar por cidades que já estão no caminho.
                boolean repeteCidade = faixa.stream()
                        .anyMatch(T -> EX.cidadesNoCaminho.contains(T.destino()));

                if (repeteCidade) continue;
                int vagas = calcularAssentosLivresNaPerna(C, faixa);
                if (vagas <= 0) continue;

                List<String> paradas = extrairParadasIntermediarias(faixa);

                TrechoConsorcioDTO dto = new TrechoConsorcioDTO(
                        meuServidorId,
                        C.getId() + "." + cidadeMapper.getNome(origem)
                                + "-" + cidadeMapper.getNome(cidadeDestino),
                        cidadeMapper.getNome(origem),
                        cidadeMapper.getNome(cidadeDestino),
                        C.getData().toString(),
                        precoAcumulado,
                        vagas,
                        paradas
                );
                // Adiciona o trecho ao prefixo atual.
                EX.prefixo.add(dto);

                try {
                    // Encontrou o destino: registra o itinerário.
                    if (cidadeDestino == EX.destinoId) {
                        adicionarResultado(EX,new ArrayList<>(EX.prefixo)
                        );
                    } else {
                        // Continua a busca a partir desta parada.
                        Set<Integer> cidadesAdicionadas = new HashSet<>();

                        for (Trecho T : faixa) {
                            cidadesAdicionadas.add(T.destino());
                        }

                        EX.cidadesNoCaminho.addAll(cidadesAdicionadas);

                        try {
                            buscarLocal(cidadeDestino, EX);
                            buscarRemoto(cidadeMapper.getNome(cidadeDestino), EX);
                        } finally {
                            EX.cidadesNoCaminho.removeAll(cidadesAdicionadas);
                        }
                    }
                } finally {
                    // Backtracking: restaura o prefixo anterior.
                    EX.prefixo.removeLast();
                }
            }
        }
    }


    /**
     * Método auxiliar para calcular os assentos livres considerando apenas os trechos da perna.
     */
    private int calcularAssentosLivresNaPerna(Carona carona, List<Trecho> trechosPerna) {
        int vagasTotais = carona.getVagasTotais();
        Map<Trecho, Set<String>> ocupacaoPorTrecho = carona.getOcupacaoPorTrecho();

        int maxOcupados = 0;
        for (Trecho t : trechosPerna) {
            Set<String> passageirosNoTrecho = ocupacaoPorTrecho.get(t);
            if (passageirosNoTrecho != null) {
                maxOcupados = Math.max(maxOcupados, passageirosNoTrecho.size());
            }
        }

        return Math.max(0, vagasTotais - maxOcupados);
    }


    private void buscarRemoto(String cidadeAtual, Execucao EX) {
        String visitadosCsv = String.join(",", EX.visitados);

        for (Vizinho V : vizinhos) {
            if (EX.resultados.size() >= MAX_ITINERARIOS || EX.consultas >= MAX_CONSULTAS) {
                return;
            }

            String chaveVizinho = ServerConstant.normalizar(V.id())
                    .trim()
                    .toLowerCase(Locale.ROOT);

            // Não consulta servidores já visitados.
            if (EX.visitados.contains(chaveVizinho)) continue;

            // Não consulta servidores suspeitos.
            if (peerClient.suspeito(V.id())) {
                log.info("[BUSCA] pulando {} (suspeito de estar fora do ar)", V.id());
                continue;
            }

            EX.consultas++;

            peerClient.buscaTrechosRemotos(V,cidadeAtual,
                    EX.destinoNome, visitadosCsv
            ).ifPresent(resp -> {
                List<ItinerarioConsorcioDTO> sufixos = resp.itinerarios() == null
                                ? List.of()
                                : resp.itinerarios();

                for (ItinerarioConsorcioDTO sufixo : sufixos) {
                    if (EX.resultados.size() >= MAX_ITINERARIOS) break;

                    if (sufixo.trechos() == null || sufixo.trechos().isEmpty()) {
                        continue;
                    }

                    List<TrechoConsorcioDTO> combinado = new ArrayList<>(EX.prefixo);
                    combinado.addAll(sufixo.trechos());
                    adicionarResultado(EX, combinado);
                }
            });
        }
    }

    private void adicionarResultado(Execucao EX, List<TrechoConsorcioDTO> trechos) {
        List<TrechoConsorcioDTO> consolidados = consolidarTrechos(trechos);

        if (EX.resultados.size() >= MAX_ITINERARIOS ||
                consolidados.isEmpty() ||
                consolidados.size() > MAX_TRECHOS_TX) {
            return;
        }

        boolean duplicado = EX.resultados.stream()
                .anyMatch(resultado ->
                        itinerariosIguais(resultado.trechos(), consolidados));

        if (duplicado) return;

        double precoTotal = consolidados.stream()
                .mapToDouble(TrechoConsorcioDTO::preco)
                .sum();

        String idItin = meuServidorId + "-" + (EX.resultados.size() + 1);

        EX.resultados.add(
                new ItinerarioConsorcioDTO(idItin, precoTotal, consolidados)
        );
    }


    // ======================= Util

    private void adicionarVisitado(Execucao EX, String id) {
        if (id == null || id.isBlank()) return;

        String chave = ServerConstant.normalizar(id)
                .trim()
                .toLowerCase(Locale.ROOT);

        if (!chave.isEmpty() &&
                !EX.visitados.contains(chave) &&
                EX.visitados.size() < MAX_VISITADOS) {
            EX.visitados.add(chave);
        }
    }

    private boolean itinerariosIguais( List<TrechoConsorcioDTO> a, List<TrechoConsorcioDTO> b) {
        if (a.size() != b.size()) return false;

        for (int i = 0; i < a.size(); i++) {
            TrechoConsorcioDTO x = a.get(i);
            TrechoConsorcioDTO y = b.get(i);

            if (!Objects.equals(x.servidor(), y.servidor()) ||
                    !Objects.equals(x.idCarona(), y.idCarona()) ||
                    !Objects.equals(x.origem(), y.origem()) ||
                    !Objects.equals(x.destino(), y.destino())) {
                return false;
            }
        }

        return true;
    }

    private List<TrechoConsorcioDTO> consolidarTrechos(
            List<TrechoConsorcioDTO> trechos) {

        List<TrechoConsorcioDTO> resultado = new ArrayList<>();

        for (TrechoConsorcioDTO atual : trechos) {
            if (!resultado.isEmpty()) {
                TrechoConsorcioDTO anterior = resultado.getLast();

                String idAnterior = idBaseBusca(anterior.idCarona());
                String idAtual = idBaseBusca(atual.idCarona());

                boolean mesmaCarona =
                        Objects.equals(anterior.servidor(), atual.servidor()) &&
                                Objects.equals(idAnterior, idAtual);

                boolean consecutivos = anterior.destino()
                        .equalsIgnoreCase(atual.origem());

                if (mesmaCarona && consecutivos) {
                    List<String> paradas = new ArrayList<>(
                            anterior.paradasIntermediarias()
                    );

                    paradas.add(anterior.destino());
                    paradas.addAll(atual.paradasIntermediarias());

                    resultado.set(
                            resultado.size() - 1,
                            new TrechoConsorcioDTO(
                                    anterior.servidor(),
                                    idAnterior + "." + anterior.origem()
                                            + "-" + atual.destino(),
                                    anterior.origem(),
                                    atual.destino(),
                                    anterior.horario(),
                                    anterior.preco() + atual.preco(),
                                    Math.min(
                                            anterior.assentos_livres(),
                                            atual.assentos_livres()
                                    ),
                                    paradas
                            )
                    );

                    continue;
                }
            }

            resultado.add(atual);
        }

        return resultado;
    }

    private String idBaseBusca(String idCarona) {
        int separador = idCarona.indexOf('.');
        return separador < 0 ? idCarona : idCarona.substring(0, separador);
    }

    private List<String> extrairParadasIntermediarias(List<Trecho> faixa) {
        if (faixa.size() <= 1) {
            return List.of();
        }

        return faixa.subList(0, faixa.size() - 1).stream()
                .map(Trecho::destino)
                .map(cidadeMapper::getNome)
                .toList();
    }

    private Integer idDaCidade(String nome) {
        if (nome == null || nome.isBlank()) return null;
        try { return cidadeMapper.traduzirParaId(nome.trim()); }
        catch (RuntimeException e) { return null; }
    }
}
