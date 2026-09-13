package org.UEFS.vaijunto.domain.caronas;

import com.fasterxml.jackson.core.type.TypeReference;
import org.UEFS.vaijunto.domain.interfaces.DataRepo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CaronaRepo extends DataRepo<Carona> {
    public CaronaRepo() {
        super("data/caronas.json", new TypeReference<Map<String, Carona>>() {});
    }

    public List<Carona> getDadosList() {
        return new ArrayList<>(dados.values());
    }
}
