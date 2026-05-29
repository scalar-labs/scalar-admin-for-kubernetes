package com.scalar.admin.kubernetes.domain.exception;

/**
 * Exception thrown when an unpause operation fails.
 *
 * <p>This exception indicates that the system failed to unpause the target pods in the Kubernetes
 * cluster after a pause operation.
 */
public class UnpauseFailedException extends PauserException {

  /**
   * Constructs a new unpause failed exception with the specified detail message.
   *
   * @param message the detail message explaining the reason for the failure
   */
  public UnpauseFailedException(String message) {
    super(message);
  }

  /**
   * Constructs a new unpause failed exception with the specified detail message and cause.
   *
   * @param message the detail message explaining the reason for the failure
   * @param cause the cause of this exception
   */
  public UnpauseFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
