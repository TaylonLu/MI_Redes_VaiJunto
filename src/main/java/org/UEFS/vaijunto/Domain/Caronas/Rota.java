package org.UEFS.vaijunto.Domain.Caronas;

import java.util.ArrayList;
import java.util.List;

public record Rota(List<Trecho> trechos) {
    public Rota() {
        this(new ArrayList<>());
    }

    public void addTrecho(Trecho T) {
        trechos.add(T);
    }

    @Override
    public String toString() {
        String dados = this.trechos.stream()
                                    .map(T -> T.inicio() + "," + T.fim())
                                    .toString();
    }
}
