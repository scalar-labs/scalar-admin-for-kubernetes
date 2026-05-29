package com.scalar.admin.kubernetes.domain.exception;

public class UnpauseFailedException extends PauserException {
  public UnpauseFailedException(String message) {
    super(message);
  }

  public UnpauseFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
