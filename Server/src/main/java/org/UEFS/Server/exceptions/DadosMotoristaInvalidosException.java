package org.UEFS.Server.exceptions;

import org.UEFS.shared.model.ServerException;
import org.UEFS.shared.enums.Status;

public class DadosMotoristaInvalidosException extends ServerException {
    public DadosMotoristaInvalidosException() {
        super(Status.VEICULO_NAO_CADASTRADO);
    }

    public DadosMotoristaInvalidosException(String message) {
        super(Status.VEICULO_NAO_CADASTRADO.getCodigo(), Status.VEICULO_NAO_CADASTRADO.getTipo(), message);
    }
}