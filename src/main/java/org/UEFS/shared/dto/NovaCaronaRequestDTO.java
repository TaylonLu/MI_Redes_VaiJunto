package org.UEFS.shared.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

public record NovaCaronaRequestDTO(
    String idMotorista, int capacidade,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime data,
    List<Trecho> trechos
) {}