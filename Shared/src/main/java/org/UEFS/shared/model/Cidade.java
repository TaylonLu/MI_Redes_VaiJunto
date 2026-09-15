package org.UEFS.shared.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Cidade extends Point {
    public String nome;
    public int id;
    public List<Integer> estradas;

    // Construtor mapeado para o Jackson ler o JSON
    @JsonCreator
    public Cidade(
            @JsonProperty("nome") String nome,
            @JsonProperty("id") int id,
            @JsonProperty("posX") int posX,
            @JsonProperty("posY") int posY,
            @JsonProperty("ligacoes") List<Integer> estradas
    ) {
        super(posX, posY); // Passa as coordenadas para a classe Point (x, y)
        this.nome = nome;
        this.id = id;
        this.estradas = estradas != null ? estradas : new ArrayList<>();
    }

    // Construtor auxiliar caso use no código manualmente
    public Cidade(int x, int y) {
        super(x, y);
        this.nome = "Desconhecida";
        this.estradas = new ArrayList<>();
    }

    @Override
    public String toString() {
        String ligacoes = "";
        if (this.estradas != null) {
            ligacoes = String.join(",", this.estradas
                    .stream()
                    .map(Object::toString)
                    .toList());
        }
        return String.join(";", nome, String.valueOf(id), String.valueOf(x), String.valueOf(y), ligacoes);
    }
}