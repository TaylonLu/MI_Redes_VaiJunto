package org.UEFS.shared.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

public record CaronaResponseDTO(
        String id, String idMotorista, int vagasTotais,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime data,
        String status,
        List<TrechoOcupacaoDTO> ocupacaoPorTrecho
) {
}
