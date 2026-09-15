package org.UEFS.Server.exceptions;

import org.UEFS.shared.model.ServerException;
import org.UEFS.shared.enums.Status;

public class IncorrectRequestException extends ServerException {
    public IncorrectRequestException() {
        super(Status.BAD_REQUEST);
    }

    public IncorrectRequestException(String message) {
        super(Status.BAD_REQUEST.getCodigo(), Status.BAD_REQUEST.getTipo(), message);
    }
}