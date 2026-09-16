package org.UEFS.Server.domain.reservas;

import com.fasterxml.jackson.core.type.TypeReference;
import org.UEFS.Server.domain.interfaces.DataRepo;

import java.util.List;
import java.util.Map;

public class ReservaRepo extends DataRepo<Reserva> {
    public ReservaRepo() {
        super("data/reservas.json", new TypeReference<Map<String, Reserva>>() {});
    }

    public List<Reserva> getByUserId(String userId) {
        return dados.values().stream()
                .filter(R -> R.getIdPassageiro().equals(userId))
                .toList();
    }


}
