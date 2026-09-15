package org.UEFS.shared.dto;

import java.util.List;

public record ItinerarioDTO(
    List<PassoItinerarioDTO> passos
) {}
