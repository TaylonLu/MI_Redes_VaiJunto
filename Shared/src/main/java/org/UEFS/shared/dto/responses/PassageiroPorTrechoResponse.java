package org.UEFS.shared.dto.responses;

import org.UEFS.shared.dto.Trecho;

import java.util.List;

public record PassageiroPorTrechoResponse(
        Trecho trecho,
        List<String> passageiros
) {}