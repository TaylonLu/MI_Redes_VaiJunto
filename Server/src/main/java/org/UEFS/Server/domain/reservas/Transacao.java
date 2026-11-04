package org.UEFS.Server.domain.reservas;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.UEFS.Server.domain.interfaces.Identificavel;
import org.UEFS.shared.dto.consorcio.TrechoReserva;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.UEFS.Server.http.relogio.ServerConstant.idLogico;

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
    private volatile Set<String> cancelamentosPendentes = new HashSet<>();

    private volatile boolean avisoAnteriorPendente;
    private volatile boolean avisoProximoPendente;
    private volatile boolean cancelamentoMotoristaPendente;

    /** Criação: sempre nasce PREPARADO, sem assentos definidos. */
    public Transacao(String id, String coordenadorId, String anterior, String proximo,
                     String idPassageiro, long relogio, List<TrechoReserva> locais) {
        this(id, coordenadorId, anterior, proximo, idPassageiro, relogio,
             System.currentTimeMillis(), locais, null, EstadoTransacao.PREPARADO,
                false, false, false, Set.of());
    }

    /** Leitura do disco. */
    @JsonCreator
    public Transacao(@JsonProperty("id") String id, @JsonProperty("coordenadorId") String coordenadorId,
                     @JsonProperty("anterior") String anterior, @JsonProperty("proximo") String proximo,
                     @JsonProperty("idPassageiro") String idPassageiro, @JsonProperty("relogio") long relogio,
                     @JsonProperty("criadoEm") long criadoEm, @JsonProperty("locais") List<TrechoReserva> locais,
                     @JsonProperty("itinerario") List<TrechoReserva> itinerario,
                     @JsonProperty("estado") EstadoTransacao estado,
                     @JsonProperty("avisoAnteriorPendente") Boolean avisoAnteriorPendente,
                     @JsonProperty("avisoProximoPendente") Boolean avisoProximoPendente,
                     @JsonProperty("cancelamentoMotoristaPendente") Boolean cancelamentoMotoristaPendente,
                     @JsonProperty("cancelamentosPendentes") Set<String> cancelamentosPendentes) {
        this.id = id;
        this.coordenadorId = coordenadorId;
        this.anterior = anterior;
        this.proximo = proximo;
        this.idPassageiro = idPassageiro;
        this.relogio = relogio;
        this.criadoEm = criadoEm;
        this.locais = List.copyOf(locais);
        this.itinerario = itinerario;
        this.estado = estado;
        this.avisoAnteriorPendente = avisoAnteriorPendente != null && avisoAnteriorPendente;
        this.avisoProximoPendente = avisoProximoPendente != null && avisoProximoPendente;
        this.cancelamentoMotoristaPendente = Boolean.TRUE.equals(cancelamentoMotoristaPendente);
        this.cancelamentosPendentes = cancelamentosPendentes == null
                ? new HashSet<>()
                : new HashSet<>(cancelamentosPendentes);
    }

    /** Cópia com os assentos escolhidos. Preserva id e criadoEm. */
    public Transacao comAssentos(List<TrechoReserva> assentos) {
        Transacao copia = new Transacao(id, coordenadorId, anterior, proximo, idPassageiro, relogio,
                criadoEm, assentos, itinerario, EstadoTransacao.PREPARADO, false, false, false, Set.of());
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

    public synchronized void adicionarCancelamentoPendente(String servidorId) {
        cancelamentosPendentes.add(servidorId);
    }
    public synchronized void confirmarCancelamento(String servidorId) {
        cancelamentosPendentes.remove(servidorId);
    }
    public synchronized Set<String> getCancelamentosPendentes() {
        return Set.copyOf(cancelamentosPendentes);
    }

    public boolean isAvisoAnteriorPendente() {
        return avisoAnteriorPendente;
    }
    public void setAvisoAnteriorPendente(boolean avisoAnteriorPendente) {
        this.avisoAnteriorPendente = avisoAnteriorPendente;
    }
    public boolean isAvisoProximoPendente() {
        return avisoProximoPendente;
    }
    public void setAvisoProximoPendente(boolean avisoProximoPendente) {
        this.avisoProximoPendente = avisoProximoPendente;
    }

    public boolean isCancelamentoMotoristaPendente() {
        return cancelamentoMotoristaPendente;
    }

    public void setCancelamentoMotoristaPendente(boolean pendente) {
        this.cancelamentoMotoristaPendente = pendente;
    }

    public boolean comitado() {
        return this.estado == EstadoTransacao.COMITADO;
    }

    public boolean souDono(String meuId) {
        return idLogico(coordenadorId)
                .equalsIgnoreCase(idLogico(meuId));
    }
}