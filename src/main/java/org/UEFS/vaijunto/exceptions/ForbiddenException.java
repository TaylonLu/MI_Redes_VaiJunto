package org.UEFS.vaijunto.exceptions;

public class ForbiddenException extends ServerException {
    public ForbiddenException(String message) {
        super(777, message);
    }
}
