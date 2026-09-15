package org.UEFS.Server.exceptions;

import org.UEFS.shared.model.ServerException;
import org.UEFS.shared.enums.Status;

public class RecursoNaoEncontradoException extends ServerException {
    public RecursoNaoEncontradoException() {
        super(Status.RECURSO_NAO_ENCONTRADO);
    }

    public RecursoNaoEncontradoException(String message) {
        super(Status.RECURSO_NAO_ENCONTRADO.getCodigo(), Status.RECURSO_NAO_ENCONTRADO.getTipo(), message);
    }
}