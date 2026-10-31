package org.UEFS.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public record Regiao(
        String ip, int porta, String nome,
        @JsonProperty("regiao_base") String regiaoBase,
        @JsonProperty("cidades_base") List<String> cidadesBase,
        @JsonProperty("conexoes_externas") List<String> conexoesExternas,
        @JsonProperty("servidores_vizinhos") List<String> vizinhos
) {
    public String id() {
        return ip + ":" + porta;
    }
    @JsonIgnore
    public List<String> getCidades() {
        List<String> cidades = new ArrayList<>(cidadesBase);
        cidades.addAll(conexoesExternas);
        return cidades;
    }
}
