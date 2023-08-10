package com.scalar.admin.k8s;

public class PauserException extends Exception {
  public PauserException(String message) {
    super(message);
  }

  public PauserException(String message, Throwable cause) {
    super(message, cause);
  }
}
