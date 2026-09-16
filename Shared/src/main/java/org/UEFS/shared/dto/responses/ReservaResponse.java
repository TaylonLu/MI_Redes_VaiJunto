package org.UEFS.shared.dto.responses;

import org.UEFS.shared.dto.ItinerarioDTO;
import org.UEFS.shared.enums.StatusReserva;

import java.time.LocalDateTime;

public record ReservaResponse(
    String idReserva,
    ItinerarioDTO itinerario,
    StatusReserva status
) {}