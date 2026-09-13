package org.UEFS.shared.dto.requests;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.UEFS.shared.dto.Trecho;

import java.time.LocalDateTime;
import java.util.List;

public record NovaCaronaRequest(
    String idMotorista, int capacidade,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime data,
    List<Trecho> trechos
) {}