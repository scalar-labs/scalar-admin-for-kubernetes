package com.scalar.admin.kubernetes.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scalar.admin.kubernetes.application.PauseApplicationService;
import com.scalar.admin.kubernetes.application.dto.PauseDurationDto;
import com.scalar.admin.kubernetes.domain.exception.PauserException;
import com.scalar.admin.kubernetes.domain.model.pause.PauseByDeploymentNameCommand;
import com.scalar.admin.kubernetes.domain.model.pause.PauseByHelmReleaseCommand;
import com.scalar.admin.kubernetes.domain.model.pause.PauseCommand;
import com.scalar.admin.kubernetes.domain.model.pause.PodDiscoveryMode;
import com.scalar.admin.kubernetes.presentation.dto.PauseRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PauseControllerTest {

  private PauseApplicationService applicationService;
  private PauseController controller;

  @BeforeEach
  void beforeEach() {
    applicationService = mock(PauseApplicationService.class);
    controller = new PauseController(applicationService);
  }

  @Nested
  @DisplayName("pause()")
  class Pause {

    @Test
    @DisplayName("executes pause successfully with HELM_RELEASE mode")
    void executesPauseSuccessfullyWithHelmReleaseMode() throws PauserException {
      // Arrange
      PauseRequest request =
          new PauseRequest(
              "test-ns",
              "helm-release",
              "test-release",
              null,
              null,
              5000,
              3000L,
              false,
              null,
              null);

      PauseDurationDto expected = new PauseDurationDto(1000L, 6000L);
      when(applicationService.execute(any(PauseCommand.class))).thenReturn(expected);

      // Act
      PauseDurationDto actual = controller.pause(request);

      // Assert
      assertThat(actual).isEqualTo(expected);
      verify(applicationService).execute(any(PauseByHelmReleaseCommand.class));
    }

    @Test
    @DisplayName("executes pause successfully with DEPLOYMENT mode")
    void executesPauseSuccessfullyWithDeploymentMode() throws PauserException {
      // Arrange
      PauseRequest request =
          new PauseRequest(
              "test-ns",
              "deployment",
              null,
              "test-deployment",
              60054,
              5000,
              3000L,
              false,
              null,
              null);

      PauseDurationDto expected = new PauseDurationDto(1000L, 6000L);
      when(applicationService.execute(any(PauseCommand.class))).thenReturn(expected);

      // Act
      PauseDurationDto actual = controller.pause(request);

      // Assert
      assertThat(actual).isEqualTo(expected);
      verify(applicationService).execute(any(PauseByDeploymentNameCommand.class));
    }

    @Test
    @DisplayName("throws IllegalArgumentException when request is null")
    void throwsIllegalArgumentExceptionWhenRequestIsNull() {
      // Act & Assert
      assertThatThrownBy(() -> controller.pause(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("request is required");
    }

    @Test
    @DisplayName("throws IllegalArgumentException when podDiscoveryMode is invalid")
    void throwsIllegalArgumentExceptionWhenPodDiscoveryModeIsInvalid() {
      // Arrange
      PauseRequest request =
          new PauseRequest(
              "test-ns",
              "invalid-mode",
              "test-release",
              null,
              null,
              5000,
              3000L,
              false,
              null,
              null);

      // Act & Assert
      assertThatThrownBy(() -> controller.pause(request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid podDiscoveryMode");
    }
  }

  @Nested
  @DisplayName("createCommand()")
  class CreateCommand {

    @Test
    @DisplayName("creates PauseByHelmReleaseCommand when mode is HELM_RELEASE")
    void createsPauseByHelmReleaseCommandWhenModeIsHelmRelease() {
      // Arrange
      PauseRequest request =
          new PauseRequest(
              "test-ns",
              "helm-release",
              "test-release",
              null,
              null,
              5000,
              3000L,
              false,
              null,
              null);

      // Act
      PauseCommand command = controller.createCommand(request, PodDiscoveryMode.HELM_RELEASE);

      // Assert
      assertThat(command).isInstanceOf(PauseByHelmReleaseCommand.class);
    }

    @Test
    @DisplayName("creates PauseByDeploymentNameCommand when mode is DEPLOYMENT")
    void createsPauseByDeploymentNameCommandWhenModeIsDeployment() {
      // Arrange
      PauseRequest request =
          new PauseRequest(
              "test-ns",
              "deployment",
              null,
              "test-deployment",
              60054,
              5000,
              3000L,
              false,
              null,
              null);

      // Act
      PauseCommand command = controller.createCommand(request, PodDiscoveryMode.DEPLOYMENT);

      // Assert
      assertThat(command).isInstanceOf(PauseByDeploymentNameCommand.class);
    }
  }

  @Nested
  @DisplayName("createHelmReleaseCommand()")
  class CreateHelmReleaseCommand {

    @Test
    @DisplayName("creates command without TLS when tlsEnabled is false")
    void createsCommandWithoutTlsWhenTlsEnabledIsFalse() {
      // Arrange
      PauseRequest request =
          new PauseRequest(
              "test-ns",
              "helm-release",
              "test-release",
              null,
              null,
              5000,
              3000L,
              false,
              null,
              null);

      // Act
      PauseByHelmReleaseCommand command = controller.createHelmReleaseCommand(request);

      // Assert
      assertThat(command.namespace()).isEqualTo("test-ns");
      assertThat(command.helmReleaseName()).isEqualTo("test-release");
      assertThat(command.pauseDuration()).isEqualTo(5000);
      assertThat(command.maxPauseWaitTime()).isEqualTo(3000L);
      assertThat(command.tlsConfig()).isNull();
    }

    @Test
    @DisplayName("creates command with TLS when tlsEnabled is true")
    void createsCommandWithTlsWhenTlsEnabledIsTrue() {
      // Arrange
      PauseRequest request =
          new PauseRequest(
              "test-ns",
              "helm-release",
              "test-release",
              null,
              null,
              5000,
              3000L,
              true,
              "dummyCert",
              "dummyAuthority");

      // Act
      PauseByHelmReleaseCommand command = controller.createHelmReleaseCommand(request);

      // Assert
      assertThat(command.namespace()).isEqualTo("test-ns");
      assertThat(command.helmReleaseName()).isEqualTo("test-release");
      assertThat(command.pauseDuration()).isEqualTo(5000);
      assertThat(command.maxPauseWaitTime()).isEqualTo(3000L);
      assertThat(command.tlsConfig()).isNotNull();
      assertThat(command.tlsConfig().caRootCert()).isEqualTo("dummyCert");
      assertThat(command.tlsConfig().overrideAuthority()).isEqualTo("dummyAuthority");
    }

    @Test
    @DisplayName("creates command with null maxPauseWaitTime when not specified")
    void createsCommandWithNullMaxPauseWaitTimeWhenNotSpecified() {
      // Arrange
      PauseRequest request =
          new PauseRequest(
              "test-ns",
              "helm-release",
              "test-release",
              null,
              null,
              5000,
              null,
              false,
              null,
              null);

      // Act
      PauseByHelmReleaseCommand command = controller.createHelmReleaseCommand(request);

      // Assert
      assertThat(command.maxPauseWaitTime()).isNull();
    }
  }

  @Nested
  @DisplayName("createDeploymentCommand()")
  class CreateDeploymentCommand {

    @Test
    @DisplayName("creates command without TLS when tlsEnabled is false")
    void createsCommandWithoutTlsWhenTlsEnabledIsFalse() {
      // Arrange
      PauseRequest request =
          new PauseRequest(
              "test-ns",
              "deployment",
              null,
              "test-deployment",
              60054,
              5000,
              3000L,
              false,
              null,
              null);

      // Act
      PauseByDeploymentNameCommand command = controller.createDeploymentCommand(request);

      // Assert
      assertThat(command.namespace()).isEqualTo("test-ns");
      assertThat(command.deploymentName()).isEqualTo("test-deployment");
      assertThat(command.adminPort()).isEqualTo(60054);
      assertThat(command.pauseDuration()).isEqualTo(5000);
      assertThat(command.maxPauseWaitTime()).isEqualTo(3000L);
      assertThat(command.tlsConfig()).isNull();
    }

    @Test
    @DisplayName("creates command with TLS when tlsEnabled is true")
    void createsCommandWithTlsWhenTlsEnabledIsTrue() {
      // Arrange
      PauseRequest request =
          new PauseRequest(
              "test-ns",
              "deployment",
              null,
              "test-deployment",
              60054,
              5000,
              3000L,
              true,
              "dummyCert",
              "dummyAuthority");

      // Act
      PauseByDeploymentNameCommand command = controller.createDeploymentCommand(request);

      // Assert
      assertThat(command.namespace()).isEqualTo("test-ns");
      assertThat(command.deploymentName()).isEqualTo("test-deployment");
      assertThat(command.adminPort()).isEqualTo(60054);
      assertThat(command.pauseDuration()).isEqualTo(5000);
      assertThat(command.maxPauseWaitTime()).isEqualTo(3000L);
      assertThat(command.tlsConfig()).isNotNull();
      assertThat(command.tlsConfig().caRootCert()).isEqualTo("dummyCert");
      assertThat(command.tlsConfig().overrideAuthority()).isEqualTo("dummyAuthority");
    }

    @Test
    @DisplayName("creates command with null maxPauseWaitTime when not specified")
    void createsCommandWithNullMaxPauseWaitTimeWhenNotSpecified() {
      // Arrange
      PauseRequest request =
          new PauseRequest(
              "test-ns",
              "deployment",
              null,
              "test-deployment",
              60054,
              5000,
              null,
              false,
              null,
              null);

      // Act
      PauseByDeploymentNameCommand command = controller.createDeploymentCommand(request);

      // Assert
      assertThat(command.maxPauseWaitTime()).isNull();
    }
  }
}
