package org.UEFS.shared.dto.consorcio;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Recusa(@JsonProperty("id_transacao") String idTransacao, String status,
                     String servidor, String motivo) {}
