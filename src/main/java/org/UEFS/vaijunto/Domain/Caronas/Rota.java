package org.UEFS.vaijunto.Domain.Caronas;

import org.UEFS.vaijunto.Domain.Interfaces.Trecho;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class Rota {
    private final List<Trecho> trechos = new ArrayList<>();

    public Rota() {

    }
    public Rota(List<Trecho> trechos) {
        this.trechos.addAll(trechos);
    }

    public void addTrecho(Trecho T) {
        trechos.add(T);
    }

    public Trecho getOrigem() {
        return trechos.getFirst();
    }

    public Trecho getDestinoFinal() {
        return trechos.getLast();
    }

    public boolean isContinua() {
        return IntStream.range(0, trechos.size() - 1)
                .allMatch(i -> trechos.get(i).fim() == trechos.get(i + 1).inicio());
    }

    public List<Trecho> getTrechos() {
        return trechos;
    }

}
