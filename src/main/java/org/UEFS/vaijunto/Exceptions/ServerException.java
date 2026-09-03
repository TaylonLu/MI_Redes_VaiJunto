package org.UEFS.vaijunto.Exceptions;

import org.UEFS.vaijunto.Server.Response;

public class ServerException extends RuntimeException {
    private final int status;
    public ServerException(int status, String message) {
        super(message);
        this.status = status;
    }

    public Response toResponse() {
        return new Response(status, this.getMessage());
    }
}
