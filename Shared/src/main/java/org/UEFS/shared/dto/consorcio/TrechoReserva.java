package org.UEFS.shared.dto.consorcio;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrechoReserva(String servidor, @JsonProperty("id_carona") String idCarona, int assento) {}