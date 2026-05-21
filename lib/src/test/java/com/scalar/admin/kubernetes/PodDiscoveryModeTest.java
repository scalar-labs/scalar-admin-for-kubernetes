package com.scalar.admin.kubernetes;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PodDiscoveryModeTest {

  @Nested
  @DisplayName("validate")
  class Validate {

    @Nested
    @DisplayName("when mode is HELM_RELEASE")
    class WhenModeIsHelmRelease {

      @Nested
      @DisplayName("with valid options (helmReleaseName only)")
      class WithValidOptions {
        @Test
        void doesNotThrowException() {
          assertThatCode(
                  () -> PodDiscoveryMode.HELM_RELEASE.validate("my-release", null, null))
              .doesNotThrowAnyException();
        }
      }

      @Nested
      @DisplayName("with null helmReleaseName")
      class WithNullHelmReleaseName {
        @Test
        void throwsIllegalArgumentException() {
          assertThatThrownBy(
                  () -> PodDiscoveryMode.HELM_RELEASE.validate(null, null, null))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessage(
                  "--release-name is required when --pod-discovery-mode is helm-release.");
        }
      }

      @Nested
      @DisplayName("with deploymentName specified")
      class WithDeploymentNameSpecified {
        @Test
        void throwsIllegalArgumentException() {
          assertThatThrownBy(
                  () ->
                      PodDiscoveryMode.HELM_RELEASE.validate(
                          "my-release", "my-deployment", null))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessage(
                  "--deployment-name and --admin-port cannot be used"
                      + " when --pod-discovery-mode is helm-release.");
        }
      }

      @Nested
      @DisplayName("with adminPort specified")
      class WithAdminPortSpecified {
        @Test
        void throwsIllegalArgumentException() {
          assertThatThrownBy(
                  () -> PodDiscoveryMode.HELM_RELEASE.validate("my-release", null, 60054))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessage(
                  "--deployment-name and --admin-port cannot be used"
                      + " when --pod-discovery-mode is helm-release.");
        }
      }

      @Nested
      @DisplayName("with both deploymentName and adminPort specified")
      class WithBothDeploymentOptionsSpecified {
        @Test
        void throwsIllegalArgumentException() {
          assertThatThrownBy(
                  () ->
                      PodDiscoveryMode.HELM_RELEASE.validate(
                          "my-release", "my-deployment", 60054))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessage(
                  "--deployment-name and --admin-port cannot be used"
                      + " when --pod-discovery-mode is helm-release.");
        }
      }
    }

    @Nested
    @DisplayName("when mode is DEPLOYMENT")
    class WhenModeIsDeployment {

      @Nested
      @DisplayName("with valid options (deploymentName and adminPort only)")
      class WithValidOptions {
        @Test
        void doesNotThrowException() {
          assertThatCode(
                  () -> PodDiscoveryMode.DEPLOYMENT.validate(null, "my-deployment", 60054))
              .doesNotThrowAnyException();
        }
      }

      @Nested
      @DisplayName("with null deploymentName")
      class WithNullDeploymentName {
        @Test
        void throwsIllegalArgumentException() {
          assertThatThrownBy(
                  () -> PodDiscoveryMode.DEPLOYMENT.validate(null, null, 60054))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessage(
                  "--deployment-name and --admin-port are required"
                      + " when --pod-discovery-mode is deployment.");
        }
      }

      @Nested
      @DisplayName("with null adminPort")
      class WithNullAdminPort {
        @Test
        void throwsIllegalArgumentException() {
          assertThatThrownBy(
                  () -> PodDiscoveryMode.DEPLOYMENT.validate(null, "my-deployment", null))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessage(
                  "--deployment-name and --admin-port are required"
                      + " when --pod-discovery-mode is deployment.");
        }
      }

      @Nested
      @DisplayName("with both deploymentName and adminPort null")
      class WithBothNull {
        @Test
        void throwsIllegalArgumentException() {
          assertThatThrownBy(
                  () -> PodDiscoveryMode.DEPLOYMENT.validate(null, null, null))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessage(
                  "--deployment-name and --admin-port are required"
                      + " when --pod-discovery-mode is deployment.");
        }
      }

      @Nested
      @DisplayName("with helmReleaseName specified")
      class WithHelmReleaseNameSpecified {
        @Test
        void throwsIllegalArgumentException() {
          assertThatThrownBy(
                  () ->
                      PodDiscoveryMode.DEPLOYMENT.validate(
                          "my-release", "my-deployment", 60054))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessage(
                  "--release-name cannot be used when --pod-discovery-mode is deployment.");
        }
      }
    }
  }
}
