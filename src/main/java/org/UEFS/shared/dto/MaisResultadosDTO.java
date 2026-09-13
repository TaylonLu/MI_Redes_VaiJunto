package org.UEFS.shared.dto;

public record MaisResultadosDTO(
        int origemDesejada, int destinoDesejado,
        String ultimoIdEnviado, int limite
) {
}
