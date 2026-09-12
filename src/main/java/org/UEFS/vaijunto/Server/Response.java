package org.UEFS.vaijunto.Server;

import org.UEFS.vaijunto.Exceptions.Status;

public class Response {
    private final int status;
    private final String tipo;
    private final String dados;
    private final int tamanho;

    public Response(Status status) {
        this.status = status.getCodigo();
        this.tipo = "";
        this.dados = status.getMensagemPadrao();
        this.tamanho = this.dados.length();
    }

    public Response(Status status, String tipo) {
        this.status = status.getCodigo();
        this.tipo = tipo;
        this.dados = status.getMensagemPadrao();
        this.tamanho = this.dados.length();
    }

    public Response(int status, String tipo,  String dados) {
        this.status = status;
        this.tipo = tipo;
        this.dados = dados;
        this.tamanho = dados.length();
    }

    public int getStatus() {
        return status;
    }

    public String toMessage() {
        return ServerGrammar.empacotarEnvelope(status, tipo, dados, "NULL");
    }

    @Override
    public String toString() {
        return toMessage();
    }
}
