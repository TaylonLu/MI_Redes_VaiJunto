package org.UEFS.Server.domain.caronas;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.UEFS.shared.dto.Trecho;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Rota {
    private final List<Trecho> trechos = new ArrayList<>();

    public Rota() {}
    @JsonCreator
    public Rota(@JsonProperty("trechos") List<Trecho> trechos) {
        if (trechos != null) {
            this.trechos.addAll(trechos);
        }
    }

    public void addTrecho(Trecho T) {
        trechos.add(T);
    }

    @JsonIgnore
    public Trecho getOrigem() {
        return trechos.getFirst();
    }

    @JsonIgnore
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
