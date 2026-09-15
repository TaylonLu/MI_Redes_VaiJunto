package org.UEFS.Server.exceptions;

import org.UEFS.shared.model.ServerException;
import org.UEFS.shared.enums.Status;

public class CaronaNaoEncontradaException extends ServerException {
    public CaronaNaoEncontradaException() {
        super(Status.RESERVA_NAO_ENCONTRADA);
    }

    public CaronaNaoEncontradaException(String message) {
        super(Status.RESERVA_NAO_ENCONTRADA.getCodigo(), Status.RESERVA_NAO_ENCONTRADA.getTipo(), message);
    }
}