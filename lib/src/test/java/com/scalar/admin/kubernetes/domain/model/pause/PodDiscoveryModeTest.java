package com.scalar.admin.kubernetes.domain.model.pause;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PodDiscoveryModeTest {

  @Nested
  @DisplayName("getValue method")
  class GetValueMethod {

    @Test
    @DisplayName("returns correct value for HELM_RELEASE")
    void returnsCorrectValueForHelmRelease() {
      // Arrange & Act & Assert
      assertThat(PodDiscoveryMode.HELM_RELEASE.getValue()).isEqualTo("helm-release");
    }
  }

  @Nested
  @DisplayName("fromValue method")
  class FromValueMethod {

    @Nested
    @DisplayName("when given valid values")
    class WhenGivenValidValues {

      @Test
      @DisplayName("converts 'helm-release' to HELM_RELEASE")
      void convertsHelmRelease() {
        // Arrange & Act & Assert
        assertThat(PodDiscoveryMode.fromValue("helm-release"))
            .isEqualTo(PodDiscoveryMode.HELM_RELEASE);
      }

      @Test
      @DisplayName("converts 'HELM-RELEASE' to HELM_RELEASE (case insensitive)")
      void convertsHelmReleaseCaseInsensitive() {
        // Arrange & Act & Assert
        assertThat(PodDiscoveryMode.fromValue("HELM-RELEASE"))
            .isEqualTo(PodDiscoveryMode.HELM_RELEASE);
      }
    }

    @Nested
    @DisplayName("when given invalid values")
    class WhenGivenInvalidValues {

      @ParameterizedTest
      @NullAndEmptySource
      @ValueSource(strings = {"  ", "   "})
      @DisplayName("throws IllegalArgumentException for null or blank")
      void throwsIllegalArgumentExceptionForNullOrBlank(String invalidValue) {
        // Arrange & Act & Assert
        assertThatThrownBy(() -> PodDiscoveryMode.fromValue(invalidValue))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("podDiscoveryMode value cannot be null or blank");
      }

      @ParameterizedTest
      @ValueSource(strings = {"invalid", "unknown", "deployment"})
      @DisplayName("throws IllegalArgumentException for unknown value")
      void throwsIllegalArgumentExceptionForUnknownValue(String invalidValue) {
        // Arrange & Act & Assert
        assertThatThrownBy(() -> PodDiscoveryMode.fromValue(invalidValue))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid podDiscoveryMode: " + invalidValue + ". Valid values are: helm-release");
      }
    }
  }

  @Nested
  @DisplayName("HELM_RELEASE mode")
  class HelmReleaseMode {

    @Nested
    @DisplayName("validate method")
    class ValidateMethod {

      @Nested
      @DisplayName("when helmReleaseName is valid")
      class WhenHelmReleaseNameIsValid {

        @Test
        @DisplayName("validates successfully")
        void validatesSuccessfully() {
          // Arrange & Act & Assert
          assertThatCode(() -> PodDiscoveryMode.HELM_RELEASE.validate("my-release"))
              .doesNotThrowAnyException();
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
          assertThatThrownBy(() -> PodDiscoveryMode.HELM_RELEASE.validate(invalidHelmReleaseName))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessage("helmReleaseName is required when podDiscoveryMode is HELM_RELEASE");
        }
      }
    }
  }
}
