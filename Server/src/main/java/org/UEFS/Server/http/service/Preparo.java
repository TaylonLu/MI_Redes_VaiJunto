package org.UEFS.Server.http.service;

import org.UEFS.shared.dto.consorcio.TrechoReserva;

import java.util.List;

public record Preparo(Resultado resultado, List<TrechoReserva> assentos, boolean repetido, String motivo) {}
