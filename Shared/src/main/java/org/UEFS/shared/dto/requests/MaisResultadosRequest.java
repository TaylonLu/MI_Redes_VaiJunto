package org.UEFS.shared.dto.requests;

public record MaisResultadosRequest(
        int origemDesejada, int destinoDesejado,
        String ultimoIdEnviado, int limite
) {
}
