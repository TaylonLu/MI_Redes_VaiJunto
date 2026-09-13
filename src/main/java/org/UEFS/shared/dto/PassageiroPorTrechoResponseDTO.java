package org.UEFS.shared.dto;

import java.util.List;

public record PassageiroPorTrechoResponseDTO(
        Trecho trecho,
        List<String> passageiros // IDs ou nomes dos passageiros naquele trecho específico
) {}