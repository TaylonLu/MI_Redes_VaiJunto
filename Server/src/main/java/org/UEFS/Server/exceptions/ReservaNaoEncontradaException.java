package org.UEFS.Server.exceptions;

import org.UEFS.shared.model.ServerException;
import org.UEFS.shared.enums.Status;

public class ReservaNaoEncontradaException extends ServerException {
    public ReservaNaoEncontradaException() {
        super(Status.RESERVA_NAO_ENCONTRADA);
    }

    public ReservaNaoEncontradaException(String message) {
        super(Status.RESERVA_NAO_ENCONTRADA.getCodigo(), Status.RESERVA_NAO_ENCONTRADA.getTipo(), message);
    }
}