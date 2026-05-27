package com.scalar.admin.kubernetes.presentation.dto;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PauseRequestTest {

  @Nested
  @DisplayName("Constructor")
  class Constructor {

    @Nested
    @DisplayName("when given valid parameters")
    class WhenGivenValidParameters {

      @Test
      @DisplayName("creates PauseRequest successfully")
      void createsPauseRequestSuccessfully() {
        // Arrange & Act
        PauseRequest request =
            new PauseRequest("default", "my-release", 5000, 30000L, false, null, null);

        // Assert
        assertThat(request).isNotNull();
        assertThat(request.namespace()).isEqualTo("default");
        assertThat(request.helmReleaseName()).isEqualTo("my-release");
        assertThat(request.pauseDuration()).isEqualTo(5000);
        assertThat(request.maxPauseWaitTime()).isEqualTo(30000L);
        assertThat(request.tlsEnabled()).isFalse();
        assertThat(request.caRootCert()).isNull();
        assertThat(request.overrideAuthority()).isNull();
      }

      @Test
      @DisplayName("creates PauseRequest with TLS enabled")
      void createsPauseRequestWithTls() {
        // Arrange & Act
        PauseRequest request =
            new PauseRequest(
                "default", "my-release", 5000, null, true, "cert-content", "authority");

        // Assert
        assertThat(request).isNotNull();
        assertThat(request.tlsEnabled()).isTrue();
        assertThat(request.caRootCert()).isEqualTo("cert-content");
        assertThat(request.overrideAuthority()).isEqualTo("authority");
      }

      @Test
      @DisplayName("creates PauseRequest with null maxPauseWaitTime")
      void createsPauseRequestWithNullMaxPauseWaitTime() {
        // Arrange & Act
        PauseRequest request =
            new PauseRequest("default", "my-release", 5000, null, false, null, null);

        // Assert
        assertThat(request).isNotNull();
        assertThat(request.maxPauseWaitTime()).isNull();
      }

      @Test
      @DisplayName("creates PauseRequest with minimal pause duration (1ms)")
      void createsPauseRequestWithMinimalPauseDuration() {
        // Arrange & Act
        PauseRequest request = new PauseRequest("default", "my-release", 1, null, false, null, null);

        // Assert
        assertThat(request).isNotNull();
        assertThat(request.pauseDuration()).isEqualTo(1);
      }
    }

    @Nested
    @DisplayName("when namespace is invalid")
    class WhenNamespaceIsInvalid {

      @ParameterizedTest
      @NullAndEmptySource
      @ValueSource(strings = {"  ", "   "})
      @DisplayName("throws IllegalArgumentException")
      void throwsIllegalArgumentException(String invalidNamespace) {
        // Arrange & Act & Assert
        assertThatThrownBy(
                () ->
                    new PauseRequest(
                        invalidNamespace, "my-release", 5000, null, false, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("namespace is required");
      }
    }

    @Nested
    @DisplayName("when helmReleaseName is invalid")
    class WhenHelmReleaseNameIsInvalid {

      @ParameterizedTest
      @NullAndEmptySource
      @ValueSource(strings = {"  ", "   "})
      @DisplayName("throws IllegalArgumentException")
      void throwsIllegalArgumentException(String invalidHelmReleaseName) {
        // Arrange & Act & Assert
        assertThatThrownBy(
                () ->
                    new PauseRequest(
                        "default", invalidHelmReleaseName, 5000, null, false, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("helmReleaseName is required");
      }
    }

    @Nested
    @DisplayName("when pauseDuration is invalid")
    class WhenPauseDurationIsInvalid {

      @ParameterizedTest
      @ValueSource(ints = {0, -1, -100, -1000})
      @DisplayName("throws IllegalArgumentException")
      void throwsIllegalArgumentException(int invalidPauseDuration) {
        // Arrange & Act & Assert
        assertThatThrownBy(
                () ->
                    new PauseRequest(
                        "default", "my-release", invalidPauseDuration, null, false, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(
                "pauseDuration must be greater than 0, but was: " + invalidPauseDuration);
      }
    }
  }
}
