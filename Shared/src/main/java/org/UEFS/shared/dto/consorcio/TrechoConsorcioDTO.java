package org.UEFS.shared.dto.consorcio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TrechoConsorcioDTO(
        String servidor,
        @JsonProperty("id_carona") String idCarona,
        String origem,
        String destino,
        String horario,
        double preco,
        @JsonProperty("assentos_livres") int assentos_livres,
        @JsonProperty("paradas_intermediarias")
        List<String> paradasIntermediarias
) {
    public TrechoConsorcioDTO {
        paradasIntermediarias = paradasIntermediarias == null
                ? List.of()
                : List.copyOf(paradasIntermediarias);
    }
}