package org.UEFS.Server.exceptions;

import org.UEFS.shared.model.ServerException;
import org.UEFS.shared.enums.Status;

public class DadosIncorretosException extends ServerException {
    public DadosIncorretosException() {
        super(Status.DADOS_INCORRETOS);
    }

    public DadosIncorretosException(String message) {
        super(Status.DADOS_INCORRETOS.getCodigo(), Status.DADOS_INCORRETOS.getTipo(), message);
    }
}