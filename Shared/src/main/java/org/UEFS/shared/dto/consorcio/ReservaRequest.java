package org.UEFS.shared.dto.consorcio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReservaRequest(@JsonProperty("id_transacao") String idTransacao,
                             @JsonProperty("coordenador_id") String coordenadorId,
                             String passageiro,
                             @JsonProperty("relogio_logico") long relogioLogico,
                             @JsonProperty("trechos_solicitados") List<TrechoSolicitado> trechosSolicitados) {}

