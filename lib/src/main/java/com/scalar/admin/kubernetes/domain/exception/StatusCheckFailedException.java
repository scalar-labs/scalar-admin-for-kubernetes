package com.scalar.admin.kubernetes.domain.exception;

/**
 * Exception thrown when a status check operation fails.
 *
 * <p>This exception indicates that the system failed to check the status of target pods during a
 * pause operation.
 */
public class StatusCheckFailedException extends PauserException {

  /**
   * Constructs a new status check failed exception with the specified detail message.
   *
   * @param message the detail message explaining the reason for the failure
   */
  public StatusCheckFailedException(String message) {
    super(message);
  }

  /**
   * Constructs a new status check failed exception with the specified detail message and cause.
   *
   * @param message the detail message explaining the reason for the failure
   * @param cause the cause of this exception
   */
  public StatusCheckFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
