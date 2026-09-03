package org.UEFS.vaijunto.Domain.Usuarios;

import org.UEFS.vaijunto.Exceptions.DadosMotoristaInvalidosException;

import java.sql.Array;

public class PerfilMotorista {
    private String cnh;
    private String placaCarro;
    private String modeloCarro;
    private double notaAvaliacao;

    public PerfilMotorista(String cnh, String placaCarro, String modeloCarro, double nota) {
        if (!cnh.matches("[0-9]{11}"))
            throw new DadosMotoristaInvalidosException("CNH inválida");

        if (!placaCarro.matches("[A-Z]{3}[0-9][A-Z0-9][0-9]{2}"))
            throw new DadosMotoristaInvalidosException("Placa inválida");

        this.cnh = cnh;
        this.placaCarro = placaCarro;
        this.modeloCarro = modeloCarro;
        this.notaAvaliacao = nota;
    }

    public String getCnh() {
        return cnh;
    }
    public void setCnh(String cnh) {
        this.cnh = cnh;
    }
    public String getPlacaCarro() {
        return placaCarro;
    }
    public void setPlacaCarro(String placaCarro) {
        this.placaCarro = placaCarro;
    }
    public String getModeloCarro() {
        return modeloCarro;
    }
    public void setModeloCarro(String modeloCarro) {
        this.modeloCarro = modeloCarro;
    }
    public double getNotaAvaliacao() {
        return notaAvaliacao;
    }
    public void setNotaAvaliacao(double notaAvaliacao) {
        this.notaAvaliacao = notaAvaliacao;
    }
    public String[] getData() {
        return new String[]{cnh, placaCarro, modeloCarro, String.valueOf(notaAvaliacao)};
    }
}