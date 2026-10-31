package org.UEFS.Server.domain.reservas;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.UEFS.Server.domain.interfaces.Identificavel;
import org.UEFS.shared.dto.consorcio.TrechoReserva;

import java.util.List;

public class Transacao implements Identificavel {
    private final String id;                      // = id_transacao
    private final String coordenadorId;
    private final String anterior;                // quem enviou o prepare
    private final String proximo;                 // próximo da cadeia ("" no último)
    private final String idPassageiro;
    private final long relogio;
    private final long criadoEm;                  // epoch ms
    private final List<TrechoReserva> locais;     // as pernas DESTE servidor, com o assento
    private volatile List<TrechoReserva> itinerario;   // completo; só o coordenador (passo 3)
    private volatile EstadoTransacao estado;
    @JsonIgnore
    private volatile boolean emProcessamento;

    /** Criação: sempre nasce PREPARADO, sem assentos definidos. */
    public Transacao(String id, String coordenadorId, String anterior, String proximo,
                     String idPassageiro, long relogio, List<TrechoReserva> locais) {
        this(id, coordenadorId, anterior, proximo, idPassageiro, relogio,
             System.currentTimeMillis(), locais, null, EstadoTransacao.PREPARADO);
    }

    /** Leitura do disco. */
    @JsonCreator
    public Transacao(@JsonProperty("id") String id, @JsonProperty("coordenadorId") String coordenadorId,
                     @JsonProperty("anterior") String anterior, @JsonProperty("proximo") String proximo,
                     @JsonProperty("idPassageiro") String idPassageiro, @JsonProperty("relogio") long relogio,
                     @JsonProperty("criadoEm") long criadoEm, @JsonProperty("locais") List<TrechoReserva> locais,
                     @JsonProperty("itinerario") List<TrechoReserva> itinerario,
                     @JsonProperty("estado") EstadoTransacao estado) {
        this.id = id; this.coordenadorId = coordenadorId; this.anterior = anterior; this.proximo = proximo;
        this.idPassageiro = idPassageiro; this.relogio = relogio; this.criadoEm = criadoEm;
        this.locais = List.copyOf(locais); this.itinerario = itinerario; this.estado = estado;
    }

    /** Cópia com os assentos escolhidos. Preserva id e criadoEm. */
    public Transacao comAssentos(List<TrechoReserva> assentos) {
        Transacao copia = new Transacao(id, coordenadorId, anterior, proximo, idPassageiro, relogio,
                criadoEm, assentos, itinerario, EstadoTransacao.PREPARADO);
        copia.emProcessamento = true;
        return copia;
    }

    @Override public String getId() { return id; }
    public String getCoordenadorId() { return coordenadorId; }
    public String getAnterior() { return anterior; }
    public String getProximo() { return proximo; }
    public String getIdPassageiro() { return idPassageiro; }
    public long getRelogio() { return relogio; }
    public long getCriadoEm() { return criadoEm; }
    public List<TrechoReserva> getLocais() { return locais; }
    public List<TrechoReserva> getItinerario() { return itinerario; }
    public void setItinerario(List<TrechoReserva> itinerario) { this.itinerario = itinerario; }
    public EstadoTransacao getEstado() { return estado; }
    public void setEstado(EstadoTransacao estado) { this.estado = estado; }
    @JsonIgnore public boolean isEmProcessamento() { return emProcessamento; }
    @JsonIgnore public void setEmProcessamento(boolean valor) { this.emProcessamento = valor; }

    public boolean comitado() {
        return this.estado == EstadoTransacao.COMITADO;
    }

    public boolean souDono(String meuId) {
        return this.coordenadorId.equalsIgnoreCase(meuId);
    }
}