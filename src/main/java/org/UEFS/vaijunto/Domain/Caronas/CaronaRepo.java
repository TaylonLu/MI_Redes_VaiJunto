package org.UEFS.vaijunto.Domain.Caronas;

import com.fasterxml.jackson.core.type.TypeReference;
import org.UEFS.vaijunto.Domain.Interfaces.DataRepo;

import java.util.ArrayList;
import java.util.List;

public class CaronaRepo extends DataRepo<Carona> {
    public CaronaRepo() {
        super("/data/caronas.json", new TypeReference<>() {});
    }

    public List<Carona> getDadosList() {
        return new ArrayList<>(dados.values());
    }
}
