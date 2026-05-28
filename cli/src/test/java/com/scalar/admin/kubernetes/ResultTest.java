package com.scalar.admin.kubernetes;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalar.admin.kubernetes.application.dto.PauseDurationDto;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ResultTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final ZoneId zoneId = ZoneId.of("Asia/Tokyo");
  private final PauseDurationDto pauseDurationDto = new PauseDurationDto(1000L, 6000L);

  @Nested
  @DisplayName("Constructor and field values")
  class ConstructorAndFieldValues {

    @Test
    @DisplayName("creates Result with helmReleaseName for HELM_RELEASE mode")
    void createsResultWithHelmReleaseNameForHelmReleaseMode() {
      // Arrange & Act
      Result result = new Result("test-ns", "test-release", null, pauseDurationDto, zoneId);

      // Assert
      assertThat(result.namespace).isEqualTo("test-ns");
      assertThat(result.helmReleaseName).isEqualTo("test-release");
      assertThat(result.deploymentName).isNull();
      assertThat(result.pauseStartTimestampMs).isEqualTo(1000L);
      assertThat(result.pauseEndTimestampMs).isEqualTo(6000L);
      assertThat(result.timezone).isEqualTo("Asia/Tokyo");
    }

    @Test
    @DisplayName("creates Result with deploymentName for DEPLOYMENT mode")
    void createsResultWithDeploymentNameForDeploymentMode() {
      // Arrange & Act
      Result result = new Result("test-ns", null, "test-deployment", pauseDurationDto, zoneId);

      // Assert
      assertThat(result.namespace).isEqualTo("test-ns");
      assertThat(result.helmReleaseName).isNull();
      assertThat(result.deploymentName).isEqualTo("test-deployment");
      assertThat(result.pauseStartTimestampMs).isEqualTo(1000L);
      assertThat(result.pauseEndTimestampMs).isEqualTo(6000L);
      assertThat(result.timezone).isEqualTo("Asia/Tokyo");
    }
  }

  @Nested
  @DisplayName("JSON serialization")
  class JsonSerialization {

    @Test
    @DisplayName("serializes to JSON without deployment_name for HELM_RELEASE mode")
    void serializesToJsonWithoutDeploymentNameForHelmReleaseMode() throws JsonProcessingException {
      // Arrange
      Result result = new Result("test-ns", "test-release", null, pauseDurationDto, zoneId);

      // Act
      String json = mapper.writeValueAsString(result);
      JsonNode jsonNode = mapper.readTree(json);

      // Assert
      assertThat(jsonNode.get("namespace").asText()).isEqualTo("test-ns");
      assertThat(jsonNode.get("helm_release_name").asText()).isEqualTo("test-release");
      assertThat(jsonNode.has("deployment_name")).isFalse();
      assertThat(jsonNode.get("pause_start_timestamp_ms").asLong()).isEqualTo(1000L);
      assertThat(jsonNode.get("pause_end_timestamp_ms").asLong()).isEqualTo(6000L);
      assertThat(jsonNode.get("timezone").asText()).isEqualTo("Asia/Tokyo");
    }

    @Test
    @DisplayName("serializes to JSON without helm_release_name for DEPLOYMENT mode")
    void serializesToJsonWithoutHelmReleaseNameForDeploymentMode() throws JsonProcessingException {
      // Arrange
      Result result = new Result("test-ns", null, "test-deployment", pauseDurationDto, zoneId);

      // Act
      String json = mapper.writeValueAsString(result);
      JsonNode jsonNode = mapper.readTree(json);

      // Assert
      assertThat(jsonNode.get("namespace").asText()).isEqualTo("test-ns");
      assertThat(jsonNode.has("helm_release_name")).isFalse();
      assertThat(jsonNode.get("deployment_name").asText()).isEqualTo("test-deployment");
      assertThat(jsonNode.get("pause_start_timestamp_ms").asLong()).isEqualTo(1000L);
      assertThat(jsonNode.get("pause_end_timestamp_ms").asLong()).isEqualTo(6000L);
      assertThat(jsonNode.get("timezone").asText()).isEqualTo("Asia/Tokyo");
    }

    @Test
    @DisplayName("calculates pause_start_date_time correctly with timezone")
    void calculatesPauseStartDateTimeCorrectlyWithTimezone() {
      // Arrange
      Result result = new Result("test-ns", "test-release", null, pauseDurationDto, zoneId);

      // Assert
      // 1970-01-01 00:00:01.000 UTC = 1970-01-01 09:00:01 Asia/Tokyo (UTC+9)
      assertThat(result.pauseStartDateTime).isEqualTo("1970-01-01T09:00:01");
    }

    @Test
    @DisplayName("calculates pause_end_date_time correctly with timezone")
    void calculatesPauseEndDateTimeCorrectlyWithTimezone() {
      // Arrange
      Result result = new Result("test-ns", "test-release", null, pauseDurationDto, zoneId);

      // Assert
      // 1970-01-01 00:00:06.000 UTC = 1970-01-01 09:00:06 Asia/Tokyo (UTC+9)
      assertThat(result.pauseEndDateTime).isEqualTo("1970-01-01T09:00:06");
    }
  }
}
