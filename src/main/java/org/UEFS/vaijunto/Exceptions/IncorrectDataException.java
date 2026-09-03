package org.UEFS.vaijunto.Exceptions;

public class IncorrectDataException extends ServerException {
  public IncorrectDataException(String message) {
    super(701, message);
  }
}
