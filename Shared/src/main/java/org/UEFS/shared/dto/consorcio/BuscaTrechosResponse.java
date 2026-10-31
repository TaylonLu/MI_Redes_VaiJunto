package org.UEFS.shared.dto.consorcio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BuscaTrechosResponse(
        String servidor,
        int total,
        List<ItinerarioConsorcioDTO> itinerarios
) {}
