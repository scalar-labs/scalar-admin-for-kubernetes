package com.scalar.admin.kubernetes.domain.model.pause;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class PauseDurationTest {

  @Nested
  @DisplayName("constructor")
  class Constructor {

    @Nested
    @DisplayName("when given valid start and end times")
    class WhenGivenValidStartAndEndTimes {

      @Test
      @DisplayName("creates PauseDuration successfully")
      void createsPauseDurationSuccessfully() {
        // Arrange
        Instant startTime = Instant.parse("2024-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2024-01-01T00:01:00Z");

        // Act
        PauseDuration duration = new PauseDuration(startTime, endTime);

        // Assert
        assertThat(duration.startTime()).isEqualTo(startTime);
        assertThat(duration.endTime()).isEqualTo(endTime);
      }

      @Test
      @DisplayName("allows same start and end time")
      void allowsSameStartAndEndTime() {
        // Arrange
        Instant time = Instant.parse("2024-01-01T00:00:00Z");

        // Act
        PauseDuration duration = new PauseDuration(time, time);

        // Assert
        assertThat(duration.startTime()).isEqualTo(time);
        assertThat(duration.endTime()).isEqualTo(time);
      }
    }

    @Nested
    @DisplayName("when given invalid parameters")
    class WhenGivenInvalidParameters {

      @Test
      @DisplayName("throws IllegalArgumentException when startTime is null")
      void throwsExceptionWhenStartTimeIsNull() {
        // Arrange
        Instant endTime = Instant.parse("2024-01-01T00:01:00Z");

        // Act & Assert
        assertThatThrownBy(() -> new PauseDuration(null, endTime))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("startTime must not be null");
      }

      @Test
      @DisplayName("throws IllegalArgumentException when endTime is null")
      void throwsExceptionWhenEndTimeIsNull() {
        // Arrange
        Instant startTime = Instant.parse("2024-01-01T00:00:00Z");

        // Act & Assert
        assertThatThrownBy(() -> new PauseDuration(startTime, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("endTime must not be null");
      }

      @Test
      @DisplayName("throws IllegalArgumentException when endTime is before startTime")
      void throwsExceptionWhenEndTimeIsBeforeStartTime() {
        // Arrange
        Instant startTime = Instant.parse("2024-01-01T00:01:00Z");
        Instant endTime = Instant.parse("2024-01-01T00:00:00Z");

        // Act & Assert
        assertThatThrownBy(() -> new PauseDuration(startTime, endTime))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("endTime must not be before startTime");
      }
    }
  }

  @Nested
  @DisplayName("equals() and hashCode()")
  class EqualsAndHashCode {

    @Nested
    @DisplayName("when comparing equal instances")
    class WhenComparingEqualInstances {

      @Test
      @DisplayName("returns true for equals() and same hashCode()")
      void returnsTrueForEqualsAndSameHashCode() {
        // Arrange
        Instant startTime = Instant.parse("2024-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2024-01-01T00:01:00Z");
        PauseDuration duration1 = new PauseDuration(startTime, endTime);
        PauseDuration duration2 = new PauseDuration(startTime, endTime);

        // Act & Assert
        assertThat(duration1).isEqualTo(duration2);
        assertThat(duration1.hashCode()).isEqualTo(duration2.hashCode());
      }
    }

    @Nested
    @DisplayName("when comparing different instances")
    class WhenComparingDifferentInstances {

      @Test
      @DisplayName("returns false for different start times")
      void returnsFalseForDifferentStartTimes() {
        // Arrange
        Instant startTime1 = Instant.parse("2024-01-01T00:00:00Z");
        Instant startTime2 = Instant.parse("2024-01-01T00:00:01Z");
        Instant endTime = Instant.parse("2024-01-01T00:01:00Z");
        PauseDuration duration1 = new PauseDuration(startTime1, endTime);
        PauseDuration duration2 = new PauseDuration(startTime2, endTime);

        // Act & Assert
        assertThat(duration1).isNotEqualTo(duration2);
      }

      @Test
      @DisplayName("returns false for different end times")
      void returnsFalseForDifferentEndTimes() {
        // Arrange
        Instant startTime = Instant.parse("2024-01-01T00:00:00Z");
        Instant endTime1 = Instant.parse("2024-01-01T00:01:00Z");
        Instant endTime2 = Instant.parse("2024-01-01T00:02:00Z");
        PauseDuration duration1 = new PauseDuration(startTime, endTime1);
        PauseDuration duration2 = new PauseDuration(startTime, endTime2);

        // Act & Assert
        assertThat(duration1).isNotEqualTo(duration2);
      }
    }
  }
}
