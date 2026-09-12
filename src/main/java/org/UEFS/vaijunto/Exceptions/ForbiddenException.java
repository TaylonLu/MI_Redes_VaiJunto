package org.UEFS.vaijunto.Exceptions;

public class ForbiddenException extends ServerException {
    public ForbiddenException(String message) {
        super(777, message);
    }
}
