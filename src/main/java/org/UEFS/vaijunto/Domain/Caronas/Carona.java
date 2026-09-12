package org.UEFS.vaijunto.Domain.Caronas;

import org.UEFS.vaijunto.DTO.TrechoOcupacaoDTO;
import org.UEFS.vaijunto.Domain.Interfaces.Identificavel;
import org.UEFS.vaijunto.Domain.Interfaces.Trecho;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


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
                                     && ocupacaoPorTrecho.get(T).size() < vagasTotais)
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

    public synchronized StatusCarona getStatus() {
        return status;
    }
    public synchronized void setStatus(StatusCarona status) {
        this.status = status;
    }

    public int getVagasTotais() {
        return this.vagasTotais;
    }

    public List<TrechoOcupacaoDTO> toTrechoOcupacao() {
        return ocupacaoPorTrecho.entrySet().stream()
                .map(E -> new TrechoOcupacaoDTO(E.getKey(), E.getValue()))
                .toList();
    }
}
