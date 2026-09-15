package org.UEFS.Server.exceptions;

import org.UEFS.shared.model.ServerException;
import org.UEFS.shared.enums.Status;

public class IncorrectDataException extends ServerException {
  public IncorrectDataException() {
    super(Status.DADOS_INCORRETOS);
  }

  public IncorrectDataException(String message) {
    super(Status.DADOS_INCORRETOS.getCodigo(), Status.DADOS_INCORRETOS.getTipo(), message);
  }
}