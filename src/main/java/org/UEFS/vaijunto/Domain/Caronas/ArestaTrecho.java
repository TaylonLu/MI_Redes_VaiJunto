package org.UEFS.vaijunto.Domain.Caronas;

import org.UEFS.vaijunto.Domain.Interfaces.Trecho;

public record ArestaTrecho(
        int destino,
        Carona carona,
        Trecho trecho
) {}
