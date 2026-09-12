package org.UEFS.vaijunto.DTO;

public record MaisResultadosDTO(
        int origemDesejada, int destinoDesejado,
        String ultimoIdEnviado, int limite
) {
}
