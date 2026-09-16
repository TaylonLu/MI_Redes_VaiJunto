package org.UEFS.Server.domain.caronas;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.UEFS.shared.dto.TrechoOcupacaoDTO;
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

    private final Map<Trecho, Set<String>> ocupacaoPorTrecho;
    private final LocalDateTime data;
    private final int vagasTotais;
    private StatusCarona status;


    public Carona(Rota rota, String id_motorista, int vagasTotais, LocalDateTime data) {
        this.id = ID_BASE + UUID.randomUUID();

        this.rota = rota;
        this.data = data;
        this.vagasTotais = vagasTotais;
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
            @JsonProperty("data") LocalDateTime data,
            @JsonProperty("vagasTotais") int vagasTotais,
            @JsonProperty("status") StatusCarona status
    ) {
        this.id = id;
        this.rota = rota;
        this.id_motorista = id_motorista;
        this.ocupacaoPorTrecho = ocupacaoPorTrecho;
        this.data = data;
        this.vagasTotais = vagasTotais;
        this.status = status;
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

    public synchronized boolean semVaga(Trecho T) {
        return !this.ocupacaoPorTrecho.containsKey(T)
                || this.ocupacaoPorTrecho.get(T).size() >= vagasTotais;
    }

    public int vagas(Trecho T) {
        return this.ocupacaoPorTrecho.containsKey(T)
                ? this.ocupacaoPorTrecho.get(T).size()
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
        }

        return true;
    }

    public synchronized void removePassageiro(String idPassageiro) {
        for (Set<String> L : ocupacaoPorTrecho.values()) {
            L.remove(idPassageiro);
        }
    }

    public synchronized boolean removerPassageiroCompletamente(String idPassageiro) {
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
}
