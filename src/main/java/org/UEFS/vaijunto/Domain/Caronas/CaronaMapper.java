package org.UEFS.vaijunto.Domain.Caronas;

import org.UEFS.vaijunto.Domain.Interfaces.BaseMapper;
import org.UEFS.vaijunto.Server.ServerGrammar;

public final class CaronaMapper implements BaseMapper<Carona> {
    @Override
    public String toString(Carona dado) {
        String atributos = String.join(ServerGrammar.ATTR_SEP,
                dado.getID(),
                dado.getId_motorista(),
                dado.getData().toString(),
                String.valueOf(dado.getVagasDisponiveis()),
                dado.getStatus().toString()
                );

        String passageiros = String.join(ServerGrammar.LIST_SEP, dado.getPassageiros());
        String rota = dado.getRota().toString();

        return atributos;
    }

    @Override
    public Carona fromString(String dados) {
        return null;
    }

    @Override
    public Class<Carona> getType() {
        return Carona.class;
    }
}
