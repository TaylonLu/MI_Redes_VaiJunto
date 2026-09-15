package org.UEFS.Server.domain.caronas;

import org.UEFS.shared.dto.Trecho;

public record ArestaTrecho(
        int destino,
        Carona carona,
        Trecho trecho
) {}
