package org.UEFS.Server.domain.caronas;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.UEFS.shared.enums.StatusCarona;
import org.UEFS.Server.domain.interfaces.Identificavel;
import org.UEFS.shared.dto.Trecho;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Carona implements Identificavel {
    private final String ID_BASE = "carona_";

    private final String id;
    private final Rota rota;
    private final String id_motorista;
    private final double valorPorTrecho;

    private final Map<Trecho, Set<String>> ocupacaoPorTrecho;
    private final LocalDateTime data;
    private final int vagasTotais;
    private StatusCarona status;

    public enum ResultadoOcupacao { OK, CARONA_FECHADA, TRECHO_INEXISTENTE, SEM_VAGA, JA_NO_TRECHO }
    public record Ocupacao(ResultadoOcupacao resultado, int assento) {}

    public Carona(Rota rota, String id_motorista, int vagasTotais, LocalDateTime data) {
        this.id = ID_BASE + UUID.randomUUID();

        this.rota = rota;
        this.data = data;
        this.vagasTotais = vagasTotais;
        this.id_motorista = id_motorista;
        this.status = StatusCarona.AGENDADA;
        this.valorPorTrecho = 0.0;

        this.ocupacaoPorTrecho = new HashMap<>();
        for (Trecho T : rota.getTrechos()) {
            this.ocupacaoPorTrecho.put(T, new HashSet<>());
        }
    }

    public Carona(Rota rota, String id_motorista, int vagasTotais, double valorPorTrecho, LocalDateTime data) {
        this.id = ID_BASE + UUID.randomUUID();

        this.rota = rota;
        this.data = data;
        this.vagasTotais = vagasTotais;
        this.valorPorTrecho = valorPorTrecho;
        this.id_motorista = id_motorista;
        this.status = StatusCarona.AGENDADA;

        this.ocupacaoPorTrecho = new HashMap<>();
        for (Trecho T : rota.getTrechos()) {
            this.ocupacaoPorTrecho.put(T, new HashSet<>());
        }
    }

    @JsonCreator
    public Carona(
            @JsonProperty("id") String id,
            @JsonProperty("rota") Rota rota,
            @JsonProperty("id_motorista") String id_motorista,
            @JsonProperty("ocupacaoPorTrecho") Map<Trecho, Set<String>> ocupacaoPorTrecho,
            @JsonProperty("dataPartida") LocalDateTime data,
            @JsonProperty("vagasTotais") int vagasTotais,
            @JsonProperty("valorPorTrecho") double valorPorTrecho,
            @JsonProperty("status") StatusCarona status
    ) {
        this.id = id;
        this.rota = rota;
        this.id_motorista = id_motorista;
        this.ocupacaoPorTrecho = ocupacaoPorTrecho;
        this.data = data;
        this.vagasTotais = vagasTotais;
        this.status = status;
        this.valorPorTrecho = valorPorTrecho;
    }

    @Override
    public String getId() {
        return id;
    }
    public Rota getRota() {
        return rota;
    }
    public String getId_motorista() {
        return id_motorista;
    }
    public LocalDateTime getData() {
        return data;
    }
    public double getValorPorTrecho() {
        return valorPorTrecho;
    }

    @JsonIgnore
    public Integer getOrigemInicial() {
        if (rota == null || rota.getTrechos().isEmpty()) return null;
        return rota.getTrechos().getFirst().origem();
    }

    public synchronized boolean semVaga(Trecho T) {
        return !this.ocupacaoPorTrecho.containsKey(T)
                || this.ocupacaoPorTrecho.get(T).size() >= vagasTotais;
    }

    public int vagas(Trecho T) {
        return this.ocupacaoPorTrecho.containsKey(T)
                ? vagasTotais - this.ocupacaoPorTrecho.get(T).size()
                : -1;
    }

    public synchronized boolean emAberto() {
        return this.status == StatusCarona.AGENDADA;
    }

    @JsonIgnore
    public synchronized Set<String>
    getPassageirosSet() {
        return ocupacaoPorTrecho.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    public synchronized HashMap<Trecho, Set<String>>
    getOcupacaoPorTrecho() {
        return new HashMap<>(ocupacaoPorTrecho);
    }

    public synchronized boolean
    addPassageiro(String idPassageiro, List<Trecho> trechosDesejados) {
        if (status != StatusCarona.AGENDADA) return false;

        if (trechosDesejados.stream()
                .allMatch(T -> ocupacaoPorTrecho.containsKey(T)
                                     && ocupacaoPorTrecho.get(T).size() >= vagasTotais)
        ) return false;

        for (Trecho T : trechosDesejados) {
            ocupacaoPorTrecho.get(T).add(idPassageiro);
            System.out.println("Adicionado " + idPassageiro + " na carona.");
        }

        return true;
    }

    /**
     * Remove completamente um passageiro de uma carona.
     * @param idPassageiro ID do passageiro a ser removido
     * @return se ele foi removido de algum trecho ou não.
     */
    public synchronized boolean removerPassageiro(String idPassageiro) {
        boolean foiRemovido = false;

        for (Set<String> T : ocupacaoPorTrecho.values()) {
            if (T.remove(idPassageiro)) foiRemovido = true;
        }

        return foiRemovido;
    }

    public synchronized boolean removerVariosPassageiros(Set<String> passageiros) {
        boolean foiRemovido = false;

        for (Set<String> T : ocupacaoPorTrecho.values()) {
            if (T.removeAll(passageiros)) foiRemovido = true;
        }

        return foiRemovido;
    }

    public synchronized StatusCarona getStatus() {
        return status;
    }
    public synchronized void setStatus(StatusCarona status) {
        this.status = status;
    }

    public int getVagasTotais() {
        return this.vagasTotais;
    }

    /**
     * Reserva uma vaga em cada trecho da perna: ou todos, ou nenhum.
     * O assento é a posição no primeiro trecho.
     * */
    public synchronized Ocupacao ocuparPerna(String idPassageiro, List<Trecho> trechos) {
        if (status != StatusCarona.AGENDADA) return new Ocupacao(ResultadoOcupacao.CARONA_FECHADA, 0);
        if (trechos.isEmpty()) return new Ocupacao(ResultadoOcupacao.TRECHO_INEXISTENTE, 0);

        for (Trecho T : trechos) {
            Set<String> ocupantes = ocupacaoPorTrecho.get(T);
            if (ocupantes == null) return new Ocupacao(ResultadoOcupacao.TRECHO_INEXISTENTE, 0);
            if (ocupantes.contains(idPassageiro)) return new Ocupacao(ResultadoOcupacao.JA_NO_TRECHO, 0);
            if (ocupantes.size() >= vagasTotais) return new Ocupacao(ResultadoOcupacao.SEM_VAGA, 0);
        }

        for (Trecho T : trechos) ocupacaoPorTrecho.get(T).add(idPassageiro);
        return new Ocupacao(ResultadoOcupacao.OK, ocupacaoPorTrecho.get(trechos.getFirst()).size());
    }

    public synchronized boolean liberarPerna(String idPassageiro, List<Trecho> trechos) {
        boolean removeu = false;
        for (Trecho T : trechos) {
            Set<String> ocupantes = ocupacaoPorTrecho.get(T);
            removeu = ocupantes != null && ocupantes.remove(idPassageiro);
        }

        return removeu;
    }
}
