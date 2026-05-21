package com.scalar.admin.kubernetes;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.ZoneId;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
class Result {

  public final String namespace;

  @JsonProperty("helm_release_name")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Nullable
  public final String helmReleaseName;

  @JsonProperty("deployment_name")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Nullable
  public final String deploymentName;

  @JsonProperty("pause_start_timestamp_ms")
  public final long pauseStartTimestampMs;

  @JsonProperty("pause_end_timestamp_ms")
  public final long pauseEndTimestampMs;

  @JsonProperty("pause_start_date_time")
  public final String pauseStartDateTime;

  @JsonProperty("pause_end_date_time")
  public final String pauseEndDateTime;

  public final String timezone;

  /** Constructor for HELM_RELEASE mode. */
  Result(String namespace, String helmReleaseName, PausedDuration pausedDuration, ZoneId zoneId) {
    this.namespace = namespace;
    this.helmReleaseName = helmReleaseName;
    this.deploymentName = null;
    this.pauseStartTimestampMs = pausedDuration.getStartTime().toEpochMilli();
    this.pauseEndTimestampMs = pausedDuration.getEndTime().toEpochMilli();
    this.pauseStartDateTime =
        pausedDuration.getStartTime().atZone(zoneId).toLocalDateTime().toString();
    this.pauseEndDateTime = pausedDuration.getEndTime().atZone(zoneId).toLocalDateTime().toString();
    this.timezone = zoneId.toString();
  }

  /** Constructor for DEPLOYMENT mode. */
  Result(
      String namespace,
      String deploymentName,
      int adminPort,
      PausedDuration pausedDuration,
      ZoneId zoneId) {
    this.namespace = namespace;
    this.helmReleaseName = null;
    this.deploymentName = deploymentName;
    this.pauseStartTimestampMs = pausedDuration.getStartTime().toEpochMilli();
    this.pauseEndTimestampMs = pausedDuration.getEndTime().toEpochMilli();
    this.pauseStartDateTime =
        pausedDuration.getStartTime().atZone(zoneId).toLocalDateTime().toString();
    this.pauseEndDateTime = pausedDuration.getEndTime().atZone(zoneId).toLocalDateTime().toString();
    this.timezone = zoneId.toString();
  }
}
