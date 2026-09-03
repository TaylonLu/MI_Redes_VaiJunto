package org.UEFS.vaijunto.Server;

import org.UEFS.vaijunto.Exceptions.Status;

import java.util.HashMap;
import java.util.Map;

public class Response {
    private int status = 200;
    private final String dados;
    private final int tamanho;

    public Response(Status status) {
        this.status = status.getCodigo();
        this.dados = status.getMensagemPadrao();
        this.tamanho = this.dados.length();
    }

    public Response(int status, String dados) {
        this.status = status;
        this.dados = dados;
        this.tamanho = dados.length();
    }

    public void setStatus(int status) {
        this.status = status;
    }
    public int getStatus() {
        return status;
    }
    public String getOutput() {
        return dados;
    }

    public String toMessage() {
        return status + "|" + dados + "|" + tamanho;
    }
}
