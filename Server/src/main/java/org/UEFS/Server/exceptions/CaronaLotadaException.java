package org.UEFS.Server.exceptions;

import org.UEFS.shared.model.ServerException;
import org.UEFS.shared.enums.Status;

public class CaronaLotadaException extends ServerException {
    public CaronaLotadaException() {
        super(Status.CARONA_LOTADA);
    }

    public CaronaLotadaException(String message) {
        super(Status.CARONA_LOTADA.getCodigo(), Status.CARONA_LOTADA.getTipo(), message);
    }
}