package org.UEFS.shared.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public record Trecho(int origem, int destino) {
    @JsonValue
    public String toJsonKey() {
        return origem + "-" + destino;
    }

    @JsonCreator
    public static Trecho fromString(String key) {
        String[] partes = key.split("-");
        return new Trecho(Integer.parseInt(partes[0]), Integer.parseInt(partes[1]));
    }
}
