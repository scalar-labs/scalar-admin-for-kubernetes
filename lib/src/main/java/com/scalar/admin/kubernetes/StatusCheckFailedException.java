package com.scalar.admin.kubernetes;

public class StatusCheckFailedException extends PauserException {
  public StatusCheckFailedException(String message) {
    super(message);
  }

  public StatusCheckFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
