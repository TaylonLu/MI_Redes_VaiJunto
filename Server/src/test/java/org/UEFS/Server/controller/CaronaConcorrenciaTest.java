package org.UEFS.Server.controller;

import org.UEFS.Server.domain.reservas.ReservaRepo;
import org.UEFS.shared.dto.ItinerarioDTO; // <-- Importação adicionada
import org.UEFS.shared.dto.Trecho;
import org.UEFS.Server.domain.caronas.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

public class CaronaConcorrenciaTest {

    @Test
    public void testConcorrenciaReservaVagaUnica() throws InterruptedException {
        // ==========================================
        // 1. ARRANGE (Cenário: 1 vaga apenas)
        // ==========================================
        Trecho trecho = new Trecho(1, 2);
        Rota rota = new Rota(List.of(trecho));

        // Criamos uma carona com capacidade total de APENAS 1 VAGA
        Carona carona = new Carona(rota, "motorista_uuid", 1, LocalDateTime.now().plusDays(1));

        // <-- ATUALIZAÇÃO DOS MOCKS AQUI -->
        CaronaRepo repoMock = mock(CaronaRepo.class);
        ReservaRepo reservaRepoMock = mock(ReservaRepo.class);
        CaronaService caronaService = new CaronaService(repoMock, reservaRepoMock);

        ArestaTrecho aresta = new ArestaTrecho(2, carona, trecho);
        List<ArestaTrecho> itinerarioEscolhido = List.of(aresta);

        // <-- DTO FICTÍCIO CRIADO AQUI -->
        ItinerarioDTO dummyItinerario = new ItinerarioDTO(new ArrayList<>());

        int numeroDePassageirosConcorrentes = 10;
        List<Future<Boolean>> resultados;
        try (ExecutorService executor = Executors.newFixedThreadPool(numeroDePassageirosConcorrentes)) {
            CountDownLatch latchDeLargada = new CountDownLatch(1);

            List<Callable<Boolean>> tarefas = new ArrayList<>();

            // Prepara 10 passageiros diferentes tentando reservar a mesma vaga única ao mesmo tempo
            for (int i = 0; i < numeroDePassageirosConcorrentes; i++) {
                final String idPassageiro = "passageiro_" + i;
                tarefas.add(() -> {
                    // Todas as threads esperam aqui até o sinal verde
                    latchDeLargada.await();
                    // <-- ATUALIZAÇÃO DA CHAMADA AO MÉTODO AQUI -->
                    return caronaService.confirmarReservaAtomica(idPassageiro, itinerarioEscolhido, dummyItinerario);
                });
            }

            // ==========================================
            // 2. ACT (Disparo Simultâneo)
            // ==========================================
            // Libera a trava para que todas as 10 threads executem no exato mesmo microssegundo
            latchDeLargada.countDown();

            resultados = executor.invokeAll(tarefas);
            executor.shutdown();
            executor.awaitTermination(3, TimeUnit.SECONDS);
        }

        // ==========================================
        // 3. ASSERT (Validações de Integridade)
        // ==========================================
        long totalSucessos = resultados.stream()
                .filter(f -> {
                    try {
                        return f.get(); // Retorna true se a reserva foi aceita
                    } catch (Exception e) {
                        return false;
                    }
                })
                .count();

        // Como a carona só tinha 1 vaga, EXATAMENTE 1 thread deve ter sucesso, e as outras 9 devem falhar.
        assertEquals(1, totalSucessos, "Apenas um passageiro pode conseguir a vaga única em ambiente concorrente!");
        assertEquals(1, carona.getPassageirosSet().size(), "O conjunto final de passageiros na carona deve ter exatamente 1 pessoa.");
    }
}