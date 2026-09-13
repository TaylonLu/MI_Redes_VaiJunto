package org.UEFS.vaijunto.exceptions;

public class IncorrectRequestException extends ServerException {
    public IncorrectRequestException(String message) {
        super(700, message);
    }
}
