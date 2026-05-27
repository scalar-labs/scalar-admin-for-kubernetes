package com.scalar.admin.kubernetes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scalar.admin.kubernetes.application.dto.PauseDurationDto;
import java.time.Instant;
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

  Result(
      String namespace, String helmReleaseName, PauseDurationDto pauseDurationDto, ZoneId zoneId) {
    this.namespace = namespace;
    this.helmReleaseName = helmReleaseName;
    this.pauseStartTimestampMs = pauseDurationDto.startTimeEpochMilli();
    this.pauseEndTimestampMs = pauseDurationDto.endTimeEpochMilli();
    this.pauseStartDateTime =
        Instant.ofEpochMilli(pauseDurationDto.startTimeEpochMilli())
            .atZone(zoneId)
            .toLocalDateTime()
            .toString();
    this.pauseEndDateTime =
        Instant.ofEpochMilli(pauseDurationDto.endTimeEpochMilli())
            .atZone(zoneId)
            .toLocalDateTime()
            .toString();
    this.timezone = zoneId.toString();
  }
}
