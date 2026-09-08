package org.UEFS.vaijunto.Domain.Interfaces;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public record Rota(List<Trecho> trechos) {
    public Rota() {
        this(new ArrayList<>());
    }

    public void addTrecho(Trecho T) {
        trechos.add(T);
    }

    @Override
    public String toString() {
        return this.trechos.stream()
                                    .map(T -> "[" + T.inicio() + "," + T.fim() + "]")
                                    .collect(Collectors.joining(","));
    }
}
