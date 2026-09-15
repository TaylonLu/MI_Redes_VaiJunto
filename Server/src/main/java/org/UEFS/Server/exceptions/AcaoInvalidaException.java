package org.UEFS.Server.exceptions;

import org.UEFS.shared.model.ServerException;
import org.UEFS.shared.enums.Status;

public class AcaoInvalidaException extends ServerException {
    public AcaoInvalidaException() {
        super(Status.ACAO_INVALIDA);
    }

    public AcaoInvalidaException(String message) {
        super(Status.ACAO_INVALIDA.getCodigo(), Status.ACAO_INVALIDA.getTipo(), message);
    }
}