package com.scalar.admin.kubernetes;

public class PauseFailedException extends PauserException {
  public PauseFailedException(String message) {
    super(message);
  }

  public PauseFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
