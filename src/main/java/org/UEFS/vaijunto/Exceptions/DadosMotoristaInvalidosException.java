package org.UEFS.vaijunto.Exceptions;

public class DadosMotoristaInvalidosException extends ServerException {
    public DadosMotoristaInvalidosException(String message) {
        super(801, message);
    }
}
