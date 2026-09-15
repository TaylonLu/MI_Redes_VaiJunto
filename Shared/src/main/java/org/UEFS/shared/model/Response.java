package org.UEFS.shared.model;

import org.UEFS.shared.enums.Status;

public class Response {
    private final int status;
    private final String tipo;
    private final String dados;
    private final int tamanho;

    public Response(Status status) {
        this.status = status.getCodigo();
        this.tipo = status.getTipo();
        this.dados = status.getMensagemPadrao();
        this.tamanho = this.dados.length();
    }

    public Response(Status status, String dados) {
        this.status = status.getCodigo();
        this.tipo = status.getTipo();
        this.dados = dados;
        this.tamanho = this.dados.length();
    }

    public Response(Status status, String tipo, String dados) {
        this.status = status.getCodigo();
        this.tipo = tipo;
        this.dados = dados;
        this.tamanho = dados.length();
    }


    public Response(int status, String tipo, String dados) {
        this.status = status;
        this.tipo = tipo;
        this.dados = dados;
        this.tamanho = dados.length();
    }

    public int getStatus() {
        return status;
    }
    public String getTipo() {
        return tipo;
    }
    public String getDados() {
        return dados;
    }


    public String toMessage() {
        return String.format("%d|%s|%s|%d", status, tipo, dados, tamanho);
    }

    @Override
    public String toString() {
        return toMessage();
    }
}
