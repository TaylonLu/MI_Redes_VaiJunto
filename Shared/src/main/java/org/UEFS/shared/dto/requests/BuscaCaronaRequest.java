package org.UEFS.shared.dto.requests;

import java.time.LocalDateTime;

public record BuscaCaronaRequest(
        int origemDesejada,
        int destinoDesejado,
        int vagasRequeridas,
        LocalDateTime data_desejada
) {
}
