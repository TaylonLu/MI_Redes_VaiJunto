package org.UEFS.vaijunto.Domain.Caronas;

import org.UEFS.vaijunto.Domain.Interfaces.BaseMapper;
import org.UEFS.vaijunto.Domain.Interfaces.DataRepo;

public class CaronaRepo extends DataRepo<Carona> {
    public CaronaRepo(BaseMapper<Carona> mapper) {
        super(mapper, "caronas.txt");
    }
}
