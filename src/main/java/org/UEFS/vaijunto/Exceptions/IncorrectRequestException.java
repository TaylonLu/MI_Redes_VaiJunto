package org.UEFS.vaijunto.Exceptions;

public class IncorrectRequestException extends ServerException {
    public IncorrectRequestException(String message) {
        super(700, message);
    }
}
