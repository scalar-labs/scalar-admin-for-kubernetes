package com.scalar.admin.k8s;

import java.time.Instant;

@Immutable
public class PausedDuration {

  private final Instant startTime;
  private final Instant endTime;

  PausedDuration(Instant startTime, Instant endTime) {
    this.startTime = startTime;
    this.endTime = endTime;
  }

  public Instant getEndTime() {
    return endTime;
  }

  public Instant getStartTime() {
    return startTime;
  }
}
