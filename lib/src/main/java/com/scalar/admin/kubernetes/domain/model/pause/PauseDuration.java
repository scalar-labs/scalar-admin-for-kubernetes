package com.scalar.admin.kubernetes.domain.model.pause;

import java.time.Instant;
import javax.annotation.concurrent.Immutable;

@Immutable
public class PauseDuration {

  private final Instant startTime;
  private final Instant endTime;

  public PauseDuration(Instant startTime, Instant endTime) {
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
