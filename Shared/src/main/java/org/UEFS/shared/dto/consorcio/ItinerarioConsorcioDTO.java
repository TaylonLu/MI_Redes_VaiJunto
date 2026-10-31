package org.UEFS.shared.dto.consorcio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ItinerarioConsorcioDTO(
        @JsonProperty("itinerario_id")String itinerarioId,
        @JsonProperty("preco_total") double precoTotal,
        List<TrechoConsorcioDTO> trechos
) {}
