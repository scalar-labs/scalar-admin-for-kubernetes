package com.scalar.admin.kubernetes.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
      @DisplayName("creates PauseRequest with helm-release mode")
      void createsPauseRequestWithHelmReleaseMode() {
        // Arrange & Act
        PauseRequest request =
            new PauseRequest(
                "default",
                "helm-release",
                "my-release",
                null,
                null,
                5000,
                30000L,
                false,
                null,
                null);

        // Assert
        assertThat(request).isNotNull();
        assertThat(request.namespace()).isEqualTo("default");
        assertThat(request.podDiscoveryMode()).isEqualTo("helm-release");
        assertThat(request.helmReleaseName()).isEqualTo("my-release");
        assertThat(request.deploymentName()).isNull();
        assertThat(request.adminPort()).isNull();
        assertThat(request.pauseDuration()).isEqualTo(5000);
        assertThat(request.maxPauseWaitTime()).isEqualTo(30000L);
        assertThat(request.tlsEnabled()).isFalse();
        assertThat(request.caRootCert()).isNull();
        assertThat(request.overrideAuthority()).isNull();
      }

      @Test
      @DisplayName("creates PauseRequest with deployment mode")
      void createsPauseRequestWithDeploymentMode() {
        // Arrange & Act
        PauseRequest request =
            new PauseRequest(
                "default",
                "deployment",
                null,
                "my-deployment",
                60054,
                5000,
                null,
                false,
                null,
                null);

        // Assert
        assertThat(request).isNotNull();
        assertThat(request.namespace()).isEqualTo("default");
        assertThat(request.podDiscoveryMode()).isEqualTo("deployment");
        assertThat(request.helmReleaseName()).isNull();
        assertThat(request.deploymentName()).isEqualTo("my-deployment");
        assertThat(request.adminPort()).isEqualTo(60054);
        assertThat(request.pauseDuration()).isEqualTo(5000);
      }

      @Test
      @DisplayName("creates PauseRequest with TLS enabled")
      void createsPauseRequestWithTls() {
        // Arrange & Act
        PauseRequest request =
            new PauseRequest(
                "default",
                "helm-release",
                "my-release",
                null,
                null,
                5000,
                null,
                true,
                "cert-content",
                "authority");

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
            new PauseRequest(
                "default", "helm-release", "my-release", null, null, 5000, null, false, null, null);

        // Assert
        assertThat(request).isNotNull();
        assertThat(request.maxPauseWaitTime()).isNull();
      }

      @Test
      @DisplayName("creates PauseRequest with minimal pause duration (1ms)")
      void createsPauseRequestWithMinimalPauseDuration() {
        // Arrange & Act
        PauseRequest request =
            new PauseRequest(
                "default", "helm-release", "my-release", null, null, 1, null, false, null, null);

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
                        invalidNamespace,
                        "helm-release",
                        "my-release",
                        null,
                        null,
                        5000,
                        null,
                        false,
                        null,
                        null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("namespace is required");
      }
    }

    @Nested
    @DisplayName("when podDiscoveryMode is invalid")
    class WhenPodDiscoveryModeIsInvalid {

      @ParameterizedTest
      @NullAndEmptySource
      @ValueSource(strings = {"  ", "   "})
      @DisplayName("throws IllegalArgumentException")
      void throwsIllegalArgumentException(String invalidPodDiscoveryMode) {
        // Arrange & Act & Assert
        assertThatThrownBy(
                () ->
                    new PauseRequest(
                        "default",
                        invalidPodDiscoveryMode,
                        "my-release",
                        null,
                        null,
                        5000,
                        null,
                        false,
                        null,
                        null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("podDiscoveryMode is required");
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
                        "default",
                        "helm-release",
                        "my-release",
                        null,
                        null,
                        invalidPauseDuration,
                        null,
                        false,
                        null,
                        null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(
                "pauseDuration must be greater than 0, but was: " + invalidPauseDuration);
      }
    }

    @Nested
    @DisplayName("when tlsEnabled is true but caRootCert is missing")
    class WhenTlsEnabledButCaRootCertIsMissing {

      @ParameterizedTest
      @NullAndEmptySource
      @ValueSource(strings = {"  ", "   "})
      @DisplayName("throws IllegalArgumentException")
      void throwsIllegalArgumentException(String invalidCaRootCert) {
        // Arrange & Act & Assert
        assertThatThrownBy(
                () ->
                    new PauseRequest(
                        "default", "helm-release", "my-release", null, null, 5000, null, true, invalidCaRootCert, "authority"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("caRootCert is required when tlsEnabled is true");
      }
    }

    @Nested
    @DisplayName("when tlsEnabled is true but overrideAuthority is missing")
    class WhenTlsEnabledButOverrideAuthorityIsMissing {

      @ParameterizedTest
      @NullAndEmptySource
      @ValueSource(strings = {"  ", "   "})
      @DisplayName("throws IllegalArgumentException")
      void throwsIllegalArgumentException(String invalidOverrideAuthority) {
        // Arrange & Act & Assert
        assertThatThrownBy(
                () ->
                    new PauseRequest(
                        "default",
                        "helm-release",
                        "my-release",
                        null,
                        null,
                        5000,
                        null,
                        true,
                        "cert-content",
                        invalidOverrideAuthority))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("overrideAuthority is required when tlsEnabled is true");
      }
    }
  }
}
