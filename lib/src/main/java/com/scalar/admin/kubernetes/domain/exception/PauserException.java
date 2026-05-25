package com.scalar.admin.kubernetes.domain.exception;

/**
 * Base exception for pause operation failures.
 *
 * <p>This exception serves as the parent class for all exceptions that may occur during pause
 * operations on Scalar product pods in a Kubernetes cluster.
 */
public class PauserException extends Exception {

  /**
   * Constructs a new pauser exception with the specified detail message.
   *
   * @param message the detail message explaining the reason for the exception
   */
  public PauserException(String message) {
    super(message);
  }

  /**
   * Constructs a new pauser exception with the specified detail message and cause.
   *
   * @param message the detail message explaining the reason for the exception
   * @param cause the cause of this exception (which can be retrieved by the {@link #getCause()}
   *     method)
   */
  public PauserException(String message, Throwable cause) {
    super(message, cause);
  }
}
