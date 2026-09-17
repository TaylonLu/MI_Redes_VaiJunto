package org.UEFS.Server.domain.usuarios;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.UEFS.shared.dto.PerfilMotoristaDTO;

public class PerfilMotorista {
    private String cnh;
    private String placaCarro;
    private String modeloCarro;
    private String corCarro;
    private double notaAvaliacao;

    public PerfilMotorista() {}
    public PerfilMotorista(String cnh, String placaCarro, String modeloCarro, String corCarro, double nota) {
//        if (!cnh.matches("[0-9]{11}"))
//            throw new DadosMotoristaInvalidosException("CNH inválida");
//
//        if (!placaCarro.matches("[A-Z]{3}[0-9][A-Z0-9][0-9]{2}"))
//            throw new DadosMotoristaInvalidosException("Placa inválida");

        this.cnh = cnh;
        this.placaCarro = placaCarro;
        this.modeloCarro = modeloCarro;
        this.corCarro = corCarro;
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
    @JsonIgnore
    public PerfilMotoristaDTO getData() {
        return new PerfilMotoristaDTO(cnh, placaCarro, modeloCarro, notaAvaliacao);
    }
}