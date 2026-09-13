package org.UEFS.vaijunto.exceptions;

public class IncorrectDataException extends ServerException {
  public IncorrectDataException(String message) {
    super(701, message);
  }
}
