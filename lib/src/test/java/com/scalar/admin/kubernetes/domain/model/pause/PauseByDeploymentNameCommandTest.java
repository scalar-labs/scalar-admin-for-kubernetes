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

class PauseByDeploymentNameCommandTest {

  @Nested
  @DisplayName("create()")
  class Create {

    @Test
    @DisplayName("creates command without TLS successfully")
    void createsCommandWithoutTls() {
      // Arrange & Act
      PauseByDeploymentNameCommand command =
          PauseByDeploymentNameCommand.create(
              "default", "my-deployment", 60054, 10000, 30000L);

      // Assert
      assertThat(command.namespace()).isEqualTo("default");
      assertThat(command.deploymentName()).isEqualTo("my-deployment");
      assertThat(command.adminPort()).isEqualTo(60054);
      assertThat(command.pauseDuration()).isEqualTo(10000);
      assertThat(command.maxPauseWaitTime()).isEqualTo(30000L);
      assertThat(command.tlsConfig()).isNull();
    }

    @Test
    @DisplayName("creates command with null maxPauseWaitTime successfully")
    void createsCommandWithNullMaxPauseWaitTime() {
      // Arrange & Act
      PauseByDeploymentNameCommand command =
          PauseByDeploymentNameCommand.create("default", "my-deployment", 60054, 10000, null);

      // Assert
      assertThat(command.maxPauseWaitTime()).isNull();
    }
  }

  @Nested
  @DisplayName("createWithTls()")
  class CreateWithTls {

    @Test
    @DisplayName("creates command with TLS successfully")
    void createsCommandWithTls() {
      // Arrange & Act
      PauseByDeploymentNameCommand command =
          PauseByDeploymentNameCommand.createWithTls(
              "default",
              "my-deployment",
              60054,
              10000,
              30000L,
              "ca-root-cert-content",
              "override-authority");

      // Assert
      assertThat(command.namespace()).isEqualTo("default");
      assertThat(command.deploymentName()).isEqualTo("my-deployment");
      assertThat(command.adminPort()).isEqualTo(60054);
      assertThat(command.pauseDuration()).isEqualTo(10000);
      assertThat(command.maxPauseWaitTime()).isEqualTo(30000L);
      assertThat(command.tlsConfig()).isNotNull();
      assertThat(command.tlsConfig().caRootCert()).isEqualTo("ca-root-cert-content");
      assertThat(command.tlsConfig().overrideAuthority()).isEqualTo("override-authority");
    }
  }

  @Nested
  @DisplayName("podDiscoveryMode()")
  class PodDiscoveryMode {

    @Test
    @DisplayName("returns DEPLOYMENT mode")
    void returnsDeploymentMode() {
      // Arrange
      PauseByDeploymentNameCommand command =
          PauseByDeploymentNameCommand.create("default", "my-deployment", 60054, 10000, null);

      // Act & Assert
      assertThat(command.podDiscoveryMode()).isEqualTo(
          com.scalar.admin.kubernetes.domain.model.pause.PodDiscoveryMode.DEPLOYMENT);
    }
  }

  @Nested
  @DisplayName("validation")
  class Validation {

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
                    PauseByDeploymentNameCommand.create(
                        invalidNamespace, "my-deployment", 60054, 10000, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("namespace is required");
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
                () ->
                    PauseByDeploymentNameCommand.create(
                        "default", invalidDeploymentName, 60054, 10000, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("deploymentName is required");
      }
    }

    @Nested
    @DisplayName("when adminPort is invalid")
    class WhenAdminPortIsInvalid {

      @ParameterizedTest
      @ValueSource(ints = {0, -1, -100, 65536, 70000})
      @DisplayName("throws IllegalArgumentException for out-of-range port")
      void throwsIllegalArgumentExceptionForOutOfRangePort(int invalidPort) {
        // Arrange & Act & Assert
        assertThatThrownBy(
                () ->
                    PauseByDeploymentNameCommand.create(
                        "default", "my-deployment", invalidPort, 10000, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("adminPort must be between 1 and 65535, but was: " + invalidPort);
      }
    }

    @Nested
    @DisplayName("when adminPort is valid")
    class WhenAdminPortIsValid {

      @ParameterizedTest
      @ValueSource(ints = {1, 80, 8080, 60054, 65535})
      @DisplayName("creates command successfully for valid port")
      void createsCommandSuccessfullyForValidPort(int validPort) {
        // Arrange & Act & Assert
        assertThatCode(
                () ->
                    PauseByDeploymentNameCommand.create(
                        "default", "my-deployment", validPort, 10000, null))
            .doesNotThrowAnyException();
      }
    }

    @Nested
    @DisplayName("when pauseDuration is invalid")
    class WhenPauseDurationIsInvalid {

      @ParameterizedTest
      @ValueSource(ints = {0, -1, -100})
      @DisplayName("throws IllegalArgumentException")
      void throwsIllegalArgumentException(int invalidPauseDuration) {
        // Arrange & Act & Assert
        assertThatThrownBy(
                () ->
                    PauseByDeploymentNameCommand.create(
                        "default", "my-deployment", 60054, invalidPauseDuration, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(
                "pauseDuration must be greater than 0 millisecond, but was: "
                    + invalidPauseDuration);
      }
    }

    @Nested
    @DisplayName("when pauseDuration is valid")
    class WhenPauseDurationIsValid {

      @ParameterizedTest
      @ValueSource(ints = {1, 10000, 100000})
      @DisplayName("creates command successfully")
      void createsCommandSuccessfully(int validPauseDuration) {
        // Arrange & Act & Assert
        assertThatCode(
                () ->
                    PauseByDeploymentNameCommand.create(
                        "default", "my-deployment", 60054, validPauseDuration, null))
            .doesNotThrowAnyException();
      }
    }
  }
}
