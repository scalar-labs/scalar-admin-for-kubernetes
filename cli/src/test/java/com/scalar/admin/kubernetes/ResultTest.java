package com.scalar.admin.kubernetes;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ResultTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Instant START_TIME = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant END_TIME = Instant.parse("2026-01-01T00:00:05Z");
  private static final PausedDuration DURATION = new PausedDuration(START_TIME, END_TIME);
  private static final ZoneId ZONE_ID = ZoneId.of("Etc/UTC");

  @Nested
  @DisplayName("when mode is HELM_RELEASE")
  class WhenModeIsHelmRelease {

    @Test
    void includesHelmReleaseName() throws Exception {
      Result result = new Result("ns", "my-release", DURATION, ZONE_ID);
      JsonNode json = MAPPER.valueToTree(result);

      assertThat(json.get("helm_release_name").asText()).isEqualTo("my-release");
    }

    @Test
    void excludesDeploymentName() throws Exception {
      Result result = new Result("ns", "my-release", DURATION, ZONE_ID);
      JsonNode json = MAPPER.valueToTree(result);

      assertThat(json.has("deployment_name")).isFalse();
    }

    @Test
    void includesTimestamps() throws Exception {
      Result result = new Result("ns", "my-release", DURATION, ZONE_ID);
      JsonNode json = MAPPER.valueToTree(result);

      assertThat(json.get("namespace").asText()).isEqualTo("ns");
      assertThat(json.get("pause_start_timestamp_ms").asLong())
          .isEqualTo(START_TIME.toEpochMilli());
      assertThat(json.get("pause_end_timestamp_ms").asLong()).isEqualTo(END_TIME.toEpochMilli());
      assertThat(json.get("timezone").asText()).isEqualTo("Etc/UTC");
    }
  }

  @Nested
  @DisplayName("when mode is DEPLOYMENT")
  class WhenModeIsDeployment {

    @Test
    void includesDeploymentName() throws Exception {
      Result result = new Result("ns", "my-deployment", 60054, DURATION, ZONE_ID);
      JsonNode json = MAPPER.valueToTree(result);

      assertThat(json.get("deployment_name").asText()).isEqualTo("my-deployment");
    }

    @Test
    void excludesHelmReleaseName() throws Exception {
      Result result = new Result("ns", "my-deployment", 60054, DURATION, ZONE_ID);
      JsonNode json = MAPPER.valueToTree(result);

      assertThat(json.has("helm_release_name")).isFalse();
    }

    @Test
    void includesTimestamps() throws Exception {
      Result result = new Result("ns", "my-deployment", 60054, DURATION, ZONE_ID);
      JsonNode json = MAPPER.valueToTree(result);

      assertThat(json.get("namespace").asText()).isEqualTo("ns");
      assertThat(json.get("pause_start_timestamp_ms").asLong())
          .isEqualTo(START_TIME.toEpochMilli());
      assertThat(json.get("pause_end_timestamp_ms").asLong()).isEqualTo(END_TIME.toEpochMilli());
      assertThat(json.get("timezone").asText()).isEqualTo("Etc/UTC");
    }
  }
}
