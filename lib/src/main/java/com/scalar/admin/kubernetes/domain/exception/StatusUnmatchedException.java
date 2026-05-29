package com.scalar.admin.kubernetes.domain.exception;

/**
 * Exception thrown when target pod status does not match the expected status.
 *
 * <p>This exception indicates that the status of target pods has changed unexpectedly during a
 * pause operation, which may indicate that the pods were updated or restarted.
 */
public class StatusUnmatchedException extends PauserException {

  /**
   * Constructs a new status unmatched exception with the specified detail message.
   *
   * @param message the detail message explaining the reason for the mismatch
   */
  public StatusUnmatchedException(String message) {
    super(message);
  }

  /**
   * Constructs a new status unmatched exception with the specified detail message and cause.
   *
   * @param message the detail message explaining the reason for the mismatch
   * @param cause the cause of this exception
   */
  public StatusUnmatchedException(String message, Throwable cause) {
    super(message, cause);
  }
}
