package org.UEFS.vaijunto.exceptions;

public class DadosMotoristaInvalidosException extends ServerException {
    public DadosMotoristaInvalidosException(String message) {
        super(801, message);
    }
}
