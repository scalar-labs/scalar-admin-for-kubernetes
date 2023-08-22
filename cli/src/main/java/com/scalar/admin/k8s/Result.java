package com.scalar.admin.k8s;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.ZoneId;
import javax.annotation.concurrent.Immutable;

@Immutable
class Result {

  public final String namespace;

  @JsonProperty("helm_release_name")
  public final String helmReleaseName;

  @JsonProperty("pause_start_timestamp_ms")
  public final long pauseStartTimestampMs;

  @JsonProperty("pause_end_timestamp_ms")
  public final long pauseEndTimestampMs;

  @JsonProperty("pause_start_date_time")
  public final String pauseStartDateTime;

  @JsonProperty("pause_end_date_time")
  public final String pauseEndDateTime;

  public final String timezone;

  Result(String namespace, String helmReleaseName, PausedDuration pausedDuration, ZoneId zoneId) {
    this.namespace = namespace;
    this.helmReleaseName = helmReleaseName;
    this.pauseStartTimestampMs = pausedDuration.getStartTime().toEpochMilli();
    this.pauseEndTimestampMs = pausedDuration.getEndTime().toEpochMilli();
    this.pauseStartDateTime =
        pausedDuration.getStartTime().atZone(zoneId).toLocalDateTime().toString();
    this.pauseEndDateTime = pausedDuration.getEndTime().atZone(zoneId).toLocalDateTime().toString();
    this.timezone = zoneId.toString();
  }
}
