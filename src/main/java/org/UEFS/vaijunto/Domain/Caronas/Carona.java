package org.UEFS.vaijunto.Domain.Caronas;

import org.UEFS.vaijunto.Domain.Interfaces.Identificavel;
import org.UEFS.vaijunto.Domain.Interfaces.Rota;

import java.time.LocalDateTime;
import java.util.UUID;


public class Carona implements Identificavel {
    private final String ID_BASE = "carona_";

    private final String id;
    private final Rota rota;
    private final String id_motorista;
    /**
     * Lista de passageiros (ID dos usuários)
     */
    private final String[] passageiros;
    private LocalDateTime data;
    private int vagasDisponiveis;
    private StatusCarona status;

    public Carona(Rota rota, String id_motorista, int vagas, LocalDateTime data) {
        this.id = ID_BASE + UUID.randomUUID();
        this.rota = rota;
        this.id_motorista = id_motorista;
        this.passageiros = new String[vagas];
        this.vagasDisponiveis = vagas;
        this.status = StatusCarona.AGENDADA;
    }

    @Override
    public String getID() {
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

    public synchronized boolean temVaga() {
        return this.vagasDisponiveis > 0;
    }
    public synchronized boolean emAberto() {
        return this.status == StatusCarona.AGENDADA;
    }
    public synchronized int getVagasDisponiveis() {
        return this.vagasDisponiveis;
    }
    public synchronized String[] getPassageiros() {
        return passageiros;
    }
    public synchronized boolean addPassageiro(String id) {
        if (vagasDisponiveis == 0 && status != StatusCarona.AGENDADA) return false;

        passageiros[--vagasDisponiveis] = id;
        return true;
    }

    public synchronized StatusCarona getStatus() {
        return status;
    }
    public synchronized void setStatus(StatusCarona status) {
        this.status = status;
    }
}
