package org.UEFS.Server.domain.reservas;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.UEFS.Server.domain.interfaces.Identificavel;
import org.UEFS.shared.dto.ItinerarioDTO;
import org.UEFS.shared.dto.responses.ReservaResponse;
import org.UEFS.shared.enums.StatusReserva;

import java.util.UUID;

public class Reserva implements Identificavel {
    private final String id;
    private final String idPassageiro;
    private final ItinerarioDTO itinerario; // Guarda os passos exatos e IDs das caronas
    private StatusReserva status;

    public Reserva(String idPassageiro, ItinerarioDTO itinerario) {
        this.id = "reserva_" + UUID.randomUUID();
        this.idPassageiro = idPassageiro;
        this.itinerario = itinerario;
        this.status = StatusReserva.ATIVA;
    }

    @JsonCreator
    public Reserva(
            @JsonProperty("id") String id,
            @JsonProperty("idPassageiro") String idPassageiro,
            @JsonProperty("itinerario") ItinerarioDTO itinerario,
            @JsonProperty("status") StatusReserva status
    ) {
        this.id = id;
        this.idPassageiro = idPassageiro;
        this.itinerario = itinerario;
        this.status = status;
    }

    @Override
    public String getId() { return id; }
    public String getIdPassageiro() { return idPassageiro; }
    public ItinerarioDTO getItinerario() { return itinerario; }
    public StatusReserva getStatus() { return status; }
    public void setStatus(StatusReserva status) { this.status = status; }

    public boolean contemCarona(String idCarona) {
        return itinerario.passos().stream()
                .anyMatch(passo -> passo.idCarona().equals(idCarona));
    }

    public ReservaResponse toData() {
        return new ReservaResponse(this.id, itinerario, this.status);
    }
}