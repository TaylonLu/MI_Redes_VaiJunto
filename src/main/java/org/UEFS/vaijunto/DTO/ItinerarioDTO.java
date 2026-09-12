package org.UEFS.vaijunto.DTO;

import java.util.List;

public record ItinerarioDTO(
    List<PassoItinerarioDTO> passos
) {}
