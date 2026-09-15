package org.UEFS.shared.model;

import org.UEFS.shared.enums.Status;

public class ServerException extends RuntimeException {
    private final int status;
    private final String tipo;

    public ServerException(int status, String message) {
        super(message);
        this.status = status;
        this.tipo = "EXCEPTION";
    }

    public ServerException(int status, String tipo, String message) {
        super(message);
        this.status = status;
        this.tipo = tipo;
    }

    public ServerException(Status status) {
        super(status.getMensagemPadrao());
        this.status = status.getCodigo();
        this.tipo = status.getTipo();
    }


    public Response toResponse() {
        return new Response(status, tipo, this.getMessage());
    }
}
