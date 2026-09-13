package org.UEFS.shared.dto.responses;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.UEFS.shared.dto.TrechoOcupacaoDTO;

import java.time.LocalDateTime;
import java.util.List;

public record CaronaResponse(
        String id, String idMotorista, int vagasTotais,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime data,
        String status,
        List<TrechoOcupacaoDTO> ocupacaoPorTrecho
) {
}
