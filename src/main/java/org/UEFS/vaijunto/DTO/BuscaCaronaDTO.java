package org.UEFS.vaijunto.DTO;

import java.time.LocalDateTime;

public record BuscaCaronaDTO(
        int origemDesejada,
        int destinoDesejado,
        int vagasRequeridas,
        LocalDateTime data_desejada
) {
}
