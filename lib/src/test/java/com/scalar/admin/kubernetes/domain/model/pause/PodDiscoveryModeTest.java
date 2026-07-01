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

    @Test
    @DisplayName("returns correct value for DEPLOYMENT")
    void returnsCorrectValueForDeployment() {
      // Arrange & Act & Assert
      assertThat(PodDiscoveryMode.DEPLOYMENT.getValue()).isEqualTo("deployment");
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

      @Test
      @DisplayName("converts 'deployment' to DEPLOYMENT")
      void convertsDeployment() {
        // Arrange & Act & Assert
        assertThat(PodDiscoveryMode.fromValue("deployment"))
            .isEqualTo(PodDiscoveryMode.DEPLOYMENT);
      }

      @Test
      @DisplayName("converts 'DEPLOYMENT' to DEPLOYMENT (case insensitive)")
      void convertsDeploymentCaseInsensitive() {
        // Arrange & Act & Assert
        assertThat(PodDiscoveryMode.fromValue("DEPLOYMENT"))
            .isEqualTo(PodDiscoveryMode.DEPLOYMENT);
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
      @ValueSource(strings = {"invalid", "unknown", "helm_release", "deploy"})
      @DisplayName("throws IllegalArgumentException for unknown value")
      void throwsIllegalArgumentExceptionForUnknownValue(String invalidValue) {
        // Arrange & Act & Assert
        assertThatThrownBy(() -> PodDiscoveryMode.fromValue(invalidValue))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(
                "Invalid podDiscoveryMode: "
                    + invalidValue
                    + ". Valid values are: helm-release, deployment");
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
          assertThatCode(() -> PodDiscoveryMode.HELM_RELEASE.validate("my-release", null, null))
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
          assertThatThrownBy(
                  () -> PodDiscoveryMode.HELM_RELEASE.validate(invalidHelmReleaseName, null, null))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessage("helmReleaseName is required when podDiscoveryMode is HELM_RELEASE");
        }
      }

      @Nested
      @DisplayName("when deploymentName is specified")
      class WhenDeploymentNameIsSpecified {

        @Test
        @DisplayName("throws IllegalArgumentException")
        void throwsIllegalArgumentException() {
          // Arrange & Act & Assert
          assertThatThrownBy(
                  () ->
                      PodDiscoveryMode.HELM_RELEASE.validate(
                          "my-release", "my-deployment", null))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessage(
                  "deploymentName and adminPort cannot be used when podDiscoveryMode is"
                      + " HELM_RELEASE");
        }
      }

      @Nested
      @DisplayName("when adminPort is specified")
      class WhenAdminPortIsSpecified {

        @Test
        @DisplayName("throws IllegalArgumentException")
        void throwsIllegalArgumentException() {
          // Arrange & Act & Assert
          assertThatThrownBy(
                  () -> PodDiscoveryMode.HELM_RELEASE.validate("my-release", null, 60054))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessage(
                  "deploymentName and adminPort cannot be used when podDiscoveryMode is"
                      + " HELM_RELEASE");
        }
      }
    }
  }

  @Nested
  @DisplayName("DEPLOYMENT mode")
  class DeploymentMode {

    @Nested
    @DisplayName("validate method")
    class ValidateMethod {

      @Nested
      @DisplayName("when deploymentName and adminPort are valid")
      class WhenDeploymentNameAndAdminPortAreValid {

        @Test
        @DisplayName("validates successfully")
        void validatesSuccessfully() {
          // Arrange & Act & Assert
          assertThatCode(
                  () -> PodDiscoveryMode.DEPLOYMENT.validate(null, "my-deployment", 60054))
              .doesNotThrowAnyException();
        }
      }

      @Nested
      @DisplayName("when deploymentName is invalid")
      class WhenDeploymentNameIsInvalid {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "   "})
        @DisplayName("throws IllegalArgumentException")
        void throwsIllegalArgumentException(String invalidDeploymentName) {
          // Arrange & Act & Assert
          assertThatThrownBy(
                  () -> PodDiscoveryMode.DEPLOYMENT.validate(null, invalidDeploymentName, 60054))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessage("deploymentName is required when podDiscoveryMode is DEPLOYMENT");
        }
      }

      @Nested
      @DisplayName("when adminPort is null")
      class WhenAdminPortIsNull {

        @Test
        @DisplayName("throws IllegalArgumentException")
        void throwsIllegalArgumentException() {
          // Arrange & Act & Assert
          assertThatThrownBy(
                  () -> PodDiscoveryMode.DEPLOYMENT.validate(null, "my-deployment", null))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessage("adminPort is required when podDiscoveryMode is DEPLOYMENT");
        }
      }

      @Nested
      @DisplayName("when helmReleaseName is specified")
      class WhenHelmReleaseNameIsSpecified {

        @Test
        @DisplayName("throws IllegalArgumentException")
        void throwsIllegalArgumentException() {
          // Arrange & Act & Assert
          assertThatThrownBy(
                  () ->
                      PodDiscoveryMode.DEPLOYMENT.validate("my-release", "my-deployment", 60054))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessage("helmReleaseName cannot be used when podDiscoveryMode is DEPLOYMENT");
        }
      }
    }
  }
}
