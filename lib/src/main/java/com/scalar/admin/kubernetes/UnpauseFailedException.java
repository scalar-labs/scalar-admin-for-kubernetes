package com.scalar.admin.kubernetes;

public class UnpauseFailedException extends PauserException {
  public UnpauseFailedException(String message) {
    super(message);
  }

  public UnpauseFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
