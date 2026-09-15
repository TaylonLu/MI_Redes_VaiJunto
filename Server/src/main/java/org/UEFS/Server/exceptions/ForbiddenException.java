package org.UEFS.Server.exceptions;

import org.UEFS.shared.model.ServerException;
import org.UEFS.shared.enums.Status;

public class ForbiddenException extends ServerException {
    public ForbiddenException() {
        super(Status.FORBIDDEN);
    }

    public ForbiddenException(String message) {
        super(Status.FORBIDDEN.getCodigo(), Status.FORBIDDEN.getTipo(), message);
    }
}