package org.UEFS.vaijunto.DTO;

import org.UEFS.vaijunto.Domain.Interfaces.Trecho;
import java.time.LocalDateTime;
import java.util.List;

public record NovaCaronaRequest(
    String idMotorista,
    int vagas,
    LocalDateTime data,
    List<Trecho> trechos
) {}