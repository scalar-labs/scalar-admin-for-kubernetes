package com.scalar.admin.kubernetes.domain.model.pause;

import java.time.Instant;

/**
 * Represents the duration of a pause operation with start and end times.
 *
 * <p>This is an immutable value object that captures the time period during which a pause
 * operation was active. The start time represents when the pause began, and the end time
 * represents when the pause ended.
 *
 * @param startTime the instant when the pause operation started
 * @param endTime the instant when the pause operation ended
 */
public record PauseDuration(Instant startTime, Instant endTime) {

  /**
   * Constructs a new pause duration with validation.
   *
   * @param startTime the instant when the pause operation started
   * @param endTime the instant when the pause operation ended
   * @throws IllegalArgumentException if startTime or endTime is null, or if endTime is before
   *     startTime
   */
  public PauseDuration {
    if (startTime == null) {
      throw new IllegalArgumentException("startTime must not be null");
    }
    if (endTime == null) {
      throw new IllegalArgumentException("endTime must not be null");
    }
    if (endTime.isBefore(startTime)) {
      throw new IllegalArgumentException("endTime must not be before startTime");
    }
  }
}
