package com.scalar.admin.kubernetes;

public class GetTargetAfterPauseFailedException extends PauserException {
  public GetTargetAfterPauseFailedException(String message) {
    super(message);
  }

  public GetTargetAfterPauseFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
