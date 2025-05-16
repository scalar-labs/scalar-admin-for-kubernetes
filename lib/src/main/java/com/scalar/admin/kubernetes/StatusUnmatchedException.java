package com.scalar.admin.kubernetes;

public class StatusUnmatchedException extends PauserException {
  public StatusUnmatchedException(String message) {
    super(message);
  }

  public StatusUnmatchedException(String message, Throwable cause) {
    super(message, cause);
  }
}
