package org.UEFS.shared.dto.consorcio;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record Resposta(int status, Object corpo) {}

