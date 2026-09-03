package org.UEFS.vaijunto.Server;

public class Request { // Dados de motoristas e passageiros package request
    private final String tipo;
    private final String dados;
    private final String token;
    private final int tamanho;

    public Request(String tipo, String dados, String token, int tamanho) {
        this.tipo = tipo;
        this.dados = dados;
        this.token = token;
        this.tamanho = tamanho;
    }

    public String toMessage() {
        return this.tipo + "|" + this.dados + "|" + this.token + "|" + this.tamanho;
    }

    public String getTipo() { return tipo; }
    public String getDados() { return dados; }
    public int getTamanho() { return tamanho; }
    public String getToken() {
        return token;
    }

    @Override
    public String toString() {
        return  "Tipo: " + tipo + "\n" +
                "Token: " + token + "\n" +
                "Dados: " + dados + "\n" +
                "Tamanho: " + tamanho + "\n";
    }
}