package org.UEFS.Server.exceptions;

import org.UEFS.shared.model.ServerException;
import org.UEFS.shared.enums.Status;

public class UsuarioJaCadastradoException extends ServerException {
    public UsuarioJaCadastradoException() {
        super(Status.EMAIL_JA_CADASTRADO);
    }

    public UsuarioJaCadastradoException(String message) {
        super(Status.EMAIL_JA_CADASTRADO.getCodigo(), Status.EMAIL_JA_CADASTRADO.getTipo(), message);
    }
}