package org.UEFS.Server.domain.caronas;

import io.javalin.http.NotFoundResponse;
import org.UEFS.Server.domain.interfaces.CaronaListener;
import org.UEFS.Server.exceptions.ForbiddenException;
import org.UEFS.shared.dto.requests.BuscaCaronaRequest;
import org.UEFS.shared.dto.ItinerarioDTO;
import org.UEFS.shared.dto.PassoItinerarioDTO;
import org.UEFS.shared.dto.Trecho;
import org.UEFS.shared.enums.StatusCarona;

import java.util.*;

public class CaronaService {
    private final CaronaRepo caronaRepo;
    private final List<CaronaListener> listeners = new ArrayList<>();

    private static final int MAX_ITINERARIOS = 5;

    public CaronaService(CaronaRepo repositorio) {
        this.caronaRepo = repositorio;
    }

    public void adicionarListener(CaronaListener listener) {
        this.listeners.add(listener);
    }

    public List<ItinerarioDTO> toItinerariosDTO(List<List<ArestaTrecho>> caminhosDoGrafo) {
        return caminhosDoGrafo.stream()
                .map(C -> {
                    List<PassoItinerarioDTO> passos = C.stream()
                            .map(aresta -> new PassoItinerarioDTO(
                                    aresta.trecho().origem(), aresta.trecho().destino(),
                                    aresta.carona().getId(), aresta.carona().getId_motorista()
                            ))
                            .toList();

                    return new ItinerarioDTO(passos);
                })
                .toList();
    }

    /**
     * Busca itinerários de 1 a N caronas (com baldeação) usando BFS sobre um grafo
     * onde cada aresta é um trecho de uma carona com vaga disponível.
     */
    public List<List<ArestaTrecho>> buscarItinerarios(BuscaCaronaRequest busca) {
        Map<Integer, List<ArestaTrecho>> grafo = new HashMap<>();
        construirGrafo(grafo, busca);

        List<List<ArestaTrecho>> itinerarios = new ArrayList<>();
        if (!grafo.containsKey(busca.origemDesejada())) return itinerarios;

        Queue<List<ArestaTrecho>> fila = new LinkedList<>();
        for (ArestaTrecho inicial : grafo.get(busca.origemDesejada())) {
            fila.add(new ArrayList<>(List.of(inicial)));
        }

        while (!fila.isEmpty()) {
            List<ArestaTrecho> caminho = fila.poll();
            ArestaTrecho ultimaAresta = caminho.getLast();
            int cidadeAtual = ultimaAresta.destino();

            if (cidadeAtual == busca.destinoDesejado()) {
                itinerarios.add(caminho);
                if (itinerarios.size() >= MAX_ITINERARIOS) break;
                continue;
            }

            if (!grafo.containsKey(cidadeAtual)) continue;

            // Cidades já visitadas neste caminho (origem + destino de cada trecho já usado),
            // para nunca sugerir um itinerário que passa duas vezes pelo mesmo lugar.
            Set<Integer> cidadesVisitadas = cidadesDoCaminho(caminho, busca.origemDesejada());

            for (ArestaTrecho proxima : grafo.get(cidadeAtual)) {
                if (cidadesVisitadas.contains(proxima.destino())) continue;

                // Não faz sentido embarcar numa carona que parte antes da anterior chegar
                // (ex.: motoristaB não pode sair de 2 antes do motoristaA te deixar lá).
                if (proxima.carona().getData().isBefore(ultimaAresta.carona().getData())) continue;

                List<ArestaTrecho> novo = new ArrayList<>(caminho);
                novo.add(proxima);
                fila.add(novo);
            }
        }

        return itinerarios;
    }

    private Set<Integer> cidadesDoCaminho(List<ArestaTrecho> caminho, int origem) {
        Set<Integer> cidades = new HashSet<>();
        cidades.add(origem);
        for (ArestaTrecho aresta : caminho) {
            cidades.add(aresta.trecho().origem());
            cidades.add(aresta.destino());
        }
        return cidades;
    }

    private void construirGrafo(Map<Integer, List<ArestaTrecho>> grafo, BuscaCaronaRequest busca) {
        for (Carona carona : caronaRepo.getDadosList()) {
            if (!carona.emAberto()) continue;

            if (!carona.getData().toLocalDate().equals(busca.data_desejada().toLocalDate()))
                continue;

            for (Trecho T : carona.getRota().getTrechos()) {
                if (carona.vagas(T) < busca.vagasRequeridas()) continue;

                grafo.computeIfAbsent(T.origem(), _ -> new ArrayList<>())
                        .add(new ArestaTrecho(T.destino(), carona, T));
            }
        }
    }

    private boolean atendeRequisito(Carona carona, BuscaCaronaRequest busca) {
        List<Trecho> trechosCarona = carona.getRota().getTrechos();

        boolean achouOrigem = false;
        boolean chegouDestino = false;
        boolean temVagas = true;

        for (Trecho T : trechosCarona) {
            if (T.origem() == busca.origemDesejada()) achouOrigem = true;

            if (achouOrigem && carona.semVaga(T)) {
                int vagasDisponiveis = carona.vagas(T);

                if (vagasDisponiveis < 0 || vagasDisponiveis > busca.vagasRequeridas()) {
                    temVagas = false;
                    break;
                }
            }

            if (achouOrigem && T.destino() == busca.destinoDesejado()) {
                chegouDestino = true;
                break;
            }
        }

        return achouOrigem && chegouDestino && temVagas;
    }

    public void cancelarCaronaETransacoes(String idCarona, String idMotorista) {
        Carona carona = caronaRepo.getByID(idCarona);

        if (carona == null) throw new NotFoundResponse("Carona não encontrada.");
        if (!carona.getId_motorista().equalsIgnoreCase(idMotorista))
            throw new ForbiddenException("Você só pode cancelar suas próprias caronas.");

        carona.setStatus(StatusCarona.CANCELADA);
        caronaRepo.salvarNoDisco();

        listeners.forEach(L -> L.aoCancelarCarona(idCarona));
    }

    public void sincronizarPorCarona(String id) {
        listeners.forEach(L -> L.aoSincronizar(id));
    }
}