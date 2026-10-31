package org.UEFS.Server.http.cluster;

import org.UEFS.Server.http.service.Vizinho;
import org.UEFS.shared.dto.consorcio.ReservaRequest;

public interface Repasse {
    ResultadoRepasse prepare(Vizinho proximo, ReservaRequest pedidoRestante, int servidoresRestantes);
    void rollback(Vizinho proximo, String idTransacao);
    boolean commit(Vizinho proximo, String idTransacao);
    boolean cancel(Vizinho proximo, String idTransacao);
}
