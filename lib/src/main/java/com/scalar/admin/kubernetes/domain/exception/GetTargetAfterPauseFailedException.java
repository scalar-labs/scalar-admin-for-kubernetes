package com.scalar.admin.kubernetes.domain.exception;

/**
 * Exception thrown when retrieving target information after a pause operation fails.
 *
 * <p>This exception indicates that the system failed to get the current state of target pods after
 * the pause operation was performed.
 */
public class GetTargetAfterPauseFailedException extends PauserException {

  /**
   * Constructs a new get target after pause failed exception with the specified detail message.
   *
   * @param message the detail message explaining the reason for the failure
   */
  public GetTargetAfterPauseFailedException(String message) {
    super(message);
  }

  /**
   * Constructs a new get target after pause failed exception with the specified detail message and
   * cause.
   *
   * @param message the detail message explaining the reason for the failure
   * @param cause the cause of this exception
   */
  public GetTargetAfterPauseFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
