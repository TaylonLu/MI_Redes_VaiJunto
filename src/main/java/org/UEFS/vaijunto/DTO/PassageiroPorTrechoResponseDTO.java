package org.UEFS.vaijunto.DTO;

import org.UEFS.vaijunto.Domain.Interfaces.Trecho;

import java.util.List;

public record PassageiroPorTrechoResponseDTO(
        Trecho trecho,
        List<String> passageiros // IDs ou nomes dos passageiros naquele trecho específico
) {}