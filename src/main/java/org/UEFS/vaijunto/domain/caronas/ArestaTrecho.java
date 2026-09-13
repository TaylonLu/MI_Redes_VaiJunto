package org.UEFS.vaijunto.domain.caronas;

import org.UEFS.shared.dto.Trecho;

public record ArestaTrecho(
        int destino,
        Carona carona,
        Trecho trecho
) {}
