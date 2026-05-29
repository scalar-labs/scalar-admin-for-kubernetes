package com.scalar.admin.kubernetes.domain.exception;

/**
 * Exception thrown when a pause operation fails.
 *
 * <p>This exception indicates that the system failed to pause the target pods in the Kubernetes
 * cluster.
 */
public class PauseFailedException extends PauserException {

  /**
   * Constructs a new pause failed exception with the specified detail message.
   *
   * @param message the detail message explaining the reason for the failure
   */
  public PauseFailedException(String message) {
    super(message);
  }

  /**
   * Constructs a new pause failed exception with the specified detail message and cause.
   *
   * @param message the detail message explaining the reason for the failure
   * @param cause the cause of this exception
   */
  public PauseFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
