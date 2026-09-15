package org.UEFS.Server.exceptions;

import org.UEFS.shared.model.ServerException;
import org.UEFS.shared.enums.Status;

public class TokenInvalidoException extends ServerException {
    public TokenInvalidoException() {
        super(Status.TOKEN_INVALIDO);
    }

    public TokenInvalidoException(String message) {
        super(Status.TOKEN_INVALIDO.getCodigo(), Status.TOKEN_INVALIDO.getTipo(), message);
    }
}