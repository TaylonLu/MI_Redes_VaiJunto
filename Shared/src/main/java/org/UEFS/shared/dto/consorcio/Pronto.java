package org.UEFS.shared.dto.consorcio;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record Pronto(@JsonProperty("id_transacao") String idTransacao, String status,
                     String servidor, List<TrechoReserva> assentos) {}
