package com.scalar.admin.kubernetes;

public class PauserException extends Exception {
  public PauserException(String message) {
    super(message);
  }

  public PauserException(String message, Throwable cause) {
    super(message, cause);
  }
}
