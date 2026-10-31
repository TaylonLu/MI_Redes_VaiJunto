package org.UEFS.Server.domain.reservas;

import com.fasterxml.jackson.core.type.TypeReference;
import org.UEFS.Server.domain.interfaces.DataRepo;
import org.UEFS.Server.http.relogio.ServerConstant;

import java.util.Map;

public class TransacaoRepo extends DataRepo<Transacao> {
    public TransacaoRepo() { super(
            "data/"+ ServerConstant.getPorta() +"/transacoes.json",
            new TypeReference<Map<String, Transacao>>() {}
        );
    }
}