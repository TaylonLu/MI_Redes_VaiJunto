package org.UEFS.Server.domain.usuarios;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.UEFS.shared.dto.OtherUserDTO;
import org.UEFS.shared.dto.SelfUserDTO;
import org.UEFS.Server.domain.interfaces.Identificavel;

import java.util.UUID;

public class Usuario implements Identificavel {
    private static String ID_BASE = "user_";

    private final String senha;
    private final String email;
    private final String ID;
    private String nome;
    private PerfilMotorista perfilMotorista;

    public Usuario(String email, String senha, String nome) {
        this.senha = senha;
        this.email = email;
        this.nome = nome;
        this.ID = ID_BASE + UUID.randomUUID();
    }

    public Usuario(String id, String email, String senha, String nome) {
        this.ID = id;
        this.senha = senha;
        this.email = email;
        this.nome = nome;
    }

    @JsonCreator
    public Usuario(
            @JsonProperty("id") String id,
            @JsonProperty("email") String email,
            @JsonProperty("senha") String senha,
            @JsonProperty("nome") String nome,
            @JsonProperty("motorista") PerfilMotorista perfilMotorista
    ) {
        this.senha = senha;
        this.email = email;
        this.nome = nome;
        this.ID = id;
        this.perfilMotorista = perfilMotorista;
    }

    public String getSenha() {
        return senha;
    }
    public String getEmail() {
        return this.email;
    }
    public String getNome() {
        return nome;
    }
    @JsonProperty("id") public String getId() {
        return ID;
    }
    public void setNome(String nome) {
        this.nome = nome;
    }

    public boolean isMotorista() {
        return this.perfilMotorista != null;
    }
    public void tornarMotorista(String cnh, String placa, String modelo, String cor) {
        tornarMotorista(cnh, placa, modelo, cor, -1.0);
    }
    public void tornarMotorista(String cnh, String placa, String modelo, String cor, double nota) {
        this.perfilMotorista = new PerfilMotorista(cnh, placa, modelo, cor, nota);
    }
    @JsonProperty("motorista")
    public PerfilMotorista getPerfilMotorista() {
        return this.perfilMotorista;
    }

    @JsonIgnore
    public SelfUserDTO getSelfData() {
        return new SelfUserDTO(
                this.ID,
                this.email,
                this.nome,
                this.perfilMotorista != null ? this.perfilMotorista.getData() : null
        );
    }

    @JsonIgnore
    public OtherUserDTO getOtherData() {
        return new OtherUserDTO(this.ID, this.email, this.nome);
    }
}