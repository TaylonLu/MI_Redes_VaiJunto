package org.UEFS.vaijunto.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.UEFS.vaijunto.Domain.Interfaces.Trecho;
import java.time.LocalDateTime;
import java.util.List;

public record NovaCaronaRequest(
    String idMotorista, int capacidade,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime data,
    List<Trecho> trechos
) {}