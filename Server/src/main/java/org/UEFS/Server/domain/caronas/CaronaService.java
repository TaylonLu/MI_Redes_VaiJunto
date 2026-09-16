package org.UEFS.Server.domain.caronas;

import org.UEFS.Server.domain.reservas.Reserva;
import org.UEFS.Server.domain.reservas.ReservaRepo;
import org.UEFS.shared.dto.ItinerarioDTO;
import org.UEFS.shared.dto.PassoItinerarioDTO;
import org.UEFS.shared.dto.Trecho;
import org.UEFS.shared.dto.requests.BuscaCaronaRequest;
import org.UEFS.shared.enums.StatusCarona;
import org.UEFS.shared.enums.StatusReserva;

import java.util.*;

public class CaronaService {
   private final CaronaRepo caronaRepo;
   private final ReservaRepo reservaRepo;

   public CaronaService(CaronaRepo repositorio, ReservaRepo reservaRepo) {
       this.caronaRepo = repositorio;
       this.reservaRepo = reservaRepo;
   }

    public List<ItinerarioDTO> toItinerariosDTO(List<List<ArestaTrecho>> caminhosDoGrafo) {
       return caminhosDoGrafo.stream()
               .map(C -> {
                   List<PassoItinerarioDTO> passos = C.stream()
                           .map(aresta -> new PassoItinerarioDTO(
                                   aresta.trecho().inicio(), aresta.trecho().fim(),
                                   aresta.carona().getId(), aresta.carona().getId_motorista()
                           ))
                           .toList();

                   return new ItinerarioDTO(passos);
               })
               .toList();
    }

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
            int cidadeAtual = caminho.getLast().destino();

            if (cidadeAtual == busca.destinoDesejado()) {
                itinerarios.add(caminho);
                if (itinerarios.size() >= 5) break;
                continue;
            }

            if (!grafo.containsKey(cidadeAtual))
                continue;

            for (ArestaTrecho proxima : grafo.get(cidadeAtual)) {
                if (caminho.stream().noneMatch(A -> A.trecho().inicio() == proxima.destino())) {
                    List<ArestaTrecho> novo = new ArrayList<>(caminho);
                    novo.add(proxima);
                    fila.add(novo);
                }
            }
        }

        return itinerarios;
    }

    private void construirGrafo(Map<Integer, List<ArestaTrecho>> grafo, BuscaCaronaRequest busca) {
        for (Carona carona : caronaRepo.getDadosList()) {
            if (!carona.emAberto()) continue;

            if (!carona.getData().toLocalDate().equals(busca.data_desejada().toLocalDate()))
                continue;

            for (Trecho T : carona.getRota().getTrechos()) {
                if (!atendeRequisito(carona, busca)) continue;
                grafo.computeIfAbsent(T.inicio(), _ -> new ArrayList<>())
                        .add(new ArestaTrecho(T.fim(), carona, T));
            }
        }
    }

    public synchronized boolean confirmarReservaAtomica(String idPassageiro, List<ArestaTrecho> itinerarioEscolhido, ItinerarioDTO itinerarioDTO) {
        for (ArestaTrecho aresta : itinerarioEscolhido) {
            if (aresta.carona().semVaga(aresta.trecho())) return false;
        }

        for (ArestaTrecho aresta : itinerarioEscolhido) {
            boolean sucesso = aresta.carona().addPassageiro(idPassageiro, List.of(aresta.trecho()));
            if (!sucesso) return false;
        }

        reservaRepo.salvar(new Reserva(idPassageiro, itinerarioDTO));

        return true;
    }

    public synchronized void cancelarCaronaEItinerarios(Carona caronaCancelada) {
        caronaCancelada.setStatus(StatusCarona.CANCELADA);

        List<Reserva> reservasAfetadas = reservaRepo.getDadosList().stream()
                .filter(R -> R.getStatus() == StatusReserva.ATIVA && R.contemCarona(caronaCancelada.getId()))
                .toList();

        for (Reserva reserva : reservasAfetadas) {
            reserva.setStatus(StatusReserva.CANCELADA);

            for (var passo : reserva.getItinerario().passos()) {
                if (passo.idCarona().equals(caronaCancelada.getId())) continue;
                Carona caronaConectada = caronaRepo.getByID(passo.idCarona());
                if (caronaConectada != null)
                    caronaConectada.removerPassageiroCompletamente(reserva.getIdPassageiro());
            }
        }
    }

    private boolean atendeRequisito(Carona carona, BuscaCaronaRequest busca) {
        List<Trecho> trechosCarona = carona.getRota().getTrechos();

        boolean achouOrigem = false;
        boolean chegouDestino = false;
        boolean temVagas = true;

        for (Trecho T : trechosCarona) {
            if (T.inicio() == busca.origemDesejada()) achouOrigem = true;

            if (achouOrigem && carona.semVaga(T)) {
                int vagasDisponiveis = carona.vagas(T);

                if (vagasDisponiveis < 0 || vagasDisponiveis > busca.vagasRequeridas()) {
                    temVagas = false;
                    break;
                }
            }

            if (achouOrigem && T.fim() == busca.destinoDesejado()) {
                chegouDestino = true;
                break;
            }
        }

        return achouOrigem && chegouDestino && temVagas;
    }

    public ReservaRepo getReservaRepo() {
        return reservaRepo;
    }
}
