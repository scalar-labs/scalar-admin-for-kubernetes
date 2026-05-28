package com.scalar.admin.kubernetes.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scalar.admin.kubernetes.application.dto.PauseDurationDto;
import com.scalar.admin.kubernetes.domain.client.ScalarAdminClient;
import com.scalar.admin.kubernetes.domain.exception.PauserException;
import com.scalar.admin.kubernetes.domain.model.pause.PauseByDeploymentNameCommand;
import com.scalar.admin.kubernetes.domain.model.pause.PauseByHelmReleaseCommand;
import com.scalar.admin.kubernetes.domain.model.pause.PauseDuration;
import com.scalar.admin.kubernetes.domain.model.pause.PauseTarget;
import com.scalar.admin.kubernetes.domain.model.pause.TlsConfig;
import com.scalar.admin.kubernetes.domain.client.KubernetesClient;
import com.scalar.admin.kubernetes.domain.service.PauseService;
import com.scalar.admin.kubernetes.domain.client.ScalarAdminClientFactory;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PauseApplicationServiceTest {

  private KubernetesClient kubernetesClient;
  private ScalarAdminClientFactory scalarAdminClientFactory;
  private PauseService pauseService;
  private PauseApplicationService applicationService;

  @BeforeEach
  void beforeEach() {
    kubernetesClient = mock(KubernetesClient.class);
    scalarAdminClientFactory = mock(ScalarAdminClientFactory.class);
    pauseService = mock(PauseService.class);
    applicationService = new PauseApplicationService(kubernetesClient, scalarAdminClientFactory, pauseService);
  }

  @Nested
  @DisplayName("Constructor")
  class Constructor {
    @Test
    @DisplayName("creates instance with valid arguments")
    void createsInstanceWithValidArguments() {
      // Act & Assert
      assertThatCode(() -> new PauseApplicationService(kubernetesClient, scalarAdminClientFactory, pauseService))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("throws IllegalArgumentException when kubernetesClient is null")
    void throwsIllegalArgumentExceptionWhenRepositoryIsNull() {
      // Act & Assert
      assertThatThrownBy(() -> new PauseApplicationService(null, scalarAdminClientFactory, pauseService))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("kubernetesClient is required");
    }

    @Test
    @DisplayName("throws IllegalArgumentException when client factory is null")
    void throwsIllegalArgumentExceptionWhenClientFactoryIsNull() {
      // Act & Assert
      assertThatThrownBy(() -> new PauseApplicationService(kubernetesClient, null, pauseService))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("clientFactory is required");
    }

    @Test
    @DisplayName("throws IllegalArgumentException when pause service is null")
    void throwsIllegalArgumentExceptionWhenPauseServiceIsNull() {
      // Act & Assert
      assertThatThrownBy(() -> new PauseApplicationService(kubernetesClient, scalarAdminClientFactory, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("pauseService is required");
    }
  }

  @Nested
  @DisplayName("execute()")
  class Execute {

    @Nested
    @DisplayName("when command is null")
    class WhenCommandIsNull {

      @Test
      @DisplayName("throws IllegalArgumentException")
      void throwsIllegalArgumentException() {
        // Act & Assert
        assertThatThrownBy(() -> applicationService.execute(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("command is required");
      }
    }

    @Nested
    @DisplayName("when command is PauseByHelmReleaseCommand")
    class WhenCommandIsPauseByHelmReleaseCommand {

      @Test
      @DisplayName("executes pause successfully with non-TLS command")
      void executesPauseSuccessfullyWithNonTlsCommand() throws PauserException {
        // Arrange
        String namespace = "test-ns";
        String helmReleaseName = "test-release";
        int pauseDuration = 5000;
        Long maxPauseWaitTime = 3000L;

        PauseTarget target = mock(PauseTarget.class);
        ScalarAdminClient client = mock(ScalarAdminClient.class);
        Instant startTime = Instant.now();
        Instant endTime = startTime.plusMillis(pauseDuration);
        PauseDuration domainPauseDuration = new PauseDuration(startTime, endTime);

        PauseByHelmReleaseCommand command =
            PauseByHelmReleaseCommand.create(
                namespace, helmReleaseName, pauseDuration, maxPauseWaitTime);

        when(kubernetesClient.resolvePauseTargetByHelmRelease(namespace, helmReleaseName)).thenReturn(target);
        when(scalarAdminClientFactory.createClient(target)).thenReturn(client);
        when(pauseService.pause(
                eq(target), any(), eq(client), eq(pauseDuration), eq(maxPauseWaitTime)))
            .thenReturn(domainPauseDuration);

        // Act
        PauseDurationDto actual = applicationService.execute(command);

        // Assert
        assertThat(actual.startTimeEpochMilli()).isEqualTo(startTime.toEpochMilli());
        assertThat(actual.endTimeEpochMilli()).isEqualTo(endTime.toEpochMilli());
        verify(kubernetesClient).resolvePauseTargetByHelmRelease(namespace, helmReleaseName);
        verify(scalarAdminClientFactory).createClient(target);
        verify(pauseService)
            .pause(eq(target), any(), eq(client), eq(pauseDuration), eq(maxPauseWaitTime));
      }

      @Test
      @DisplayName("executes pause successfully with TLS command")
      void executesPauseSuccessfullyWithTlsCommand() throws PauserException {
        // Arrange
        String namespace = "test-ns";
        String helmReleaseName = "test-release";
        int pauseDuration = 5000;
        Long maxPauseWaitTime = 3000L;
        String caRootCert = "dummyCert";
        String overrideAuthority = "dummyAuthority";

        PauseTarget target = mock(PauseTarget.class);
        ScalarAdminClient client = mock(ScalarAdminClient.class);
        Instant startTime = Instant.now();
        Instant endTime = startTime.plusMillis(pauseDuration);
        PauseDuration domainPauseDuration = new PauseDuration(startTime, endTime);

        PauseByHelmReleaseCommand command =
            PauseByHelmReleaseCommand.createWithTls(
                namespace,
                helmReleaseName,
                pauseDuration,
                maxPauseWaitTime,
                caRootCert,
                overrideAuthority);

        when(kubernetesClient.resolvePauseTargetByHelmRelease(namespace, helmReleaseName)).thenReturn(target);
        when(scalarAdminClientFactory.createClient(eq(target), any(TlsConfig.class))).thenReturn(client);
        when(pauseService.pause(
                eq(target), any(), eq(client), eq(pauseDuration), eq(maxPauseWaitTime)))
            .thenReturn(domainPauseDuration);

        // Act
        PauseDurationDto actual = applicationService.execute(command);

        // Assert
        assertThat(actual.startTimeEpochMilli()).isEqualTo(startTime.toEpochMilli());
        assertThat(actual.endTimeEpochMilli()).isEqualTo(endTime.toEpochMilli());
        verify(kubernetesClient).resolvePauseTargetByHelmRelease(namespace, helmReleaseName);
        verify(scalarAdminClientFactory).createClient(eq(target), any(TlsConfig.class));
        verify(pauseService)
            .pause(eq(target), any(), eq(client), eq(pauseDuration), eq(maxPauseWaitTime));
      }

      @Test
      @DisplayName("throws PauserException when kubernetesClient throws exception")
      void throwsPauserExceptionWhenRepositoryThrowsException() throws PauserException {
        // Arrange
        String namespace = "test-ns";
        String helmReleaseName = "test-release";

        PauseByHelmReleaseCommand command =
            PauseByHelmReleaseCommand.create(namespace, helmReleaseName, 5000, 3000L);

        when(kubernetesClient.resolvePauseTargetByHelmRelease(namespace, helmReleaseName))
            .thenThrow(new RuntimeException("Repository error"));

        // Act & Assert
        assertThatThrownBy(() -> applicationService.execute(command))
            .isInstanceOf(PauserException.class)
            .hasMessage("Failed to find the target pods to pause.");
      }

      @Test
      @DisplayName("throws PauserException when client factory throws exception")
      void throwsPauserExceptionWhenClientFactoryThrowsException() throws PauserException {
        // Arrange
        String namespace = "test-ns";
        String helmReleaseName = "test-release";

        PauseTarget target = mock(PauseTarget.class);
        PauseByHelmReleaseCommand command =
            PauseByHelmReleaseCommand.create(namespace, helmReleaseName, 5000, 3000L);

        when(kubernetesClient.resolvePauseTargetByHelmRelease(namespace, helmReleaseName)).thenReturn(target);
        when(scalarAdminClientFactory.createClient(target))
            .thenThrow(new RuntimeException("Client factory error"));

        // Act & Assert
        assertThatThrownBy(() -> applicationService.execute(command))
            .isInstanceOf(PauserException.class)
            .hasMessage("Failed to initialize the Scalar Admin client.");
      }
    }

    @Nested
    @DisplayName("when command is PauseByDeploymentNameCommand")
    class WhenCommandIsPauseByDeploymentNameCommand {

      @Test
      @DisplayName("executes pause successfully with non-TLS command")
      void executesPauseSuccessfullyWithNonTlsCommand() throws PauserException {
        // Arrange
        String namespace = "test-ns";
        String deploymentName = "test-deployment";
        int adminPort = 60054;
        int pauseDuration = 5000;
        Long maxPauseWaitTime = 3000L;

        PauseTarget target = mock(PauseTarget.class);
        ScalarAdminClient client = mock(ScalarAdminClient.class);
        Instant startTime = Instant.now();
        Instant endTime = startTime.plusMillis(pauseDuration);
        PauseDuration domainPauseDuration = new PauseDuration(startTime, endTime);

        PauseByDeploymentNameCommand command =
            PauseByDeploymentNameCommand.create(
                namespace, deploymentName, adminPort, pauseDuration, maxPauseWaitTime);

        when(kubernetesClient.resolvePauseTargetByDeploymentName(namespace, deploymentName, adminPort))
            .thenReturn(target);
        when(scalarAdminClientFactory.createClient(target)).thenReturn(client);
        when(pauseService.pause(
                eq(target), any(), eq(client), eq(pauseDuration), eq(maxPauseWaitTime)))
            .thenReturn(domainPauseDuration);

        // Act
        PauseDurationDto actual = applicationService.execute(command);

        // Assert
        assertThat(actual.startTimeEpochMilli()).isEqualTo(startTime.toEpochMilli());
        assertThat(actual.endTimeEpochMilli()).isEqualTo(endTime.toEpochMilli());
        verify(kubernetesClient).resolvePauseTargetByDeploymentName(namespace, deploymentName, adminPort);
        verify(scalarAdminClientFactory).createClient(target);
        verify(pauseService)
            .pause(eq(target), any(), eq(client), eq(pauseDuration), eq(maxPauseWaitTime));
      }

      @Test
      @DisplayName("executes pause successfully with TLS command")
      void executesPauseSuccessfullyWithTlsCommand() throws PauserException {
        // Arrange
        String namespace = "test-ns";
        String deploymentName = "test-deployment";
        int adminPort = 60054;
        int pauseDuration = 5000;
        Long maxPauseWaitTime = 3000L;
        String caRootCert = "dummyCert";
        String overrideAuthority = "dummyAuthority";

        PauseTarget target = mock(PauseTarget.class);
        ScalarAdminClient client = mock(ScalarAdminClient.class);
        Instant startTime = Instant.now();
        Instant endTime = startTime.plusMillis(pauseDuration);
        PauseDuration domainPauseDuration = new PauseDuration(startTime, endTime);

        PauseByDeploymentNameCommand command =
            PauseByDeploymentNameCommand.createWithTls(
                namespace,
                deploymentName,
                adminPort,
                pauseDuration,
                maxPauseWaitTime,
                caRootCert,
                overrideAuthority);

        when(kubernetesClient.resolvePauseTargetByDeploymentName(namespace, deploymentName, adminPort))
            .thenReturn(target);
        when(scalarAdminClientFactory.createClient(eq(target), any(TlsConfig.class))).thenReturn(client);
        when(pauseService.pause(
                eq(target), any(), eq(client), eq(pauseDuration), eq(maxPauseWaitTime)))
            .thenReturn(domainPauseDuration);

        // Act
        PauseDurationDto actual = applicationService.execute(command);

        // Assert
        assertThat(actual.startTimeEpochMilli()).isEqualTo(startTime.toEpochMilli());
        assertThat(actual.endTimeEpochMilli()).isEqualTo(endTime.toEpochMilli());
        verify(kubernetesClient).resolvePauseTargetByDeploymentName(namespace, deploymentName, adminPort);
        verify(scalarAdminClientFactory).createClient(eq(target), any(TlsConfig.class));
        verify(pauseService)
            .pause(eq(target), any(), eq(client), eq(pauseDuration), eq(maxPauseWaitTime));
      }

      @Test
      @DisplayName("throws PauserException when kubernetesClient throws exception")
      void throwsPauserExceptionWhenRepositoryThrowsException() throws PauserException {
        // Arrange
        String namespace = "test-ns";
        String deploymentName = "test-deployment";
        int adminPort = 60054;

        PauseByDeploymentNameCommand command =
            PauseByDeploymentNameCommand.create(namespace, deploymentName, adminPort, 5000, 3000L);

        when(kubernetesClient.resolvePauseTargetByDeploymentName(namespace, deploymentName, adminPort))
            .thenThrow(new RuntimeException("Repository error"));

        // Act & Assert
        assertThatThrownBy(() -> applicationService.execute(command))
            .isInstanceOf(PauserException.class)
            .hasMessage("Failed to find the target pods to pause.");
      }

      @Test
      @DisplayName("throws PauserException when client factory throws exception")
      void throwsPauserExceptionWhenClientFactoryThrowsException() throws PauserException {
        // Arrange
        String namespace = "test-ns";
        String deploymentName = "test-deployment";
        int adminPort = 60054;

        PauseTarget target = mock(PauseTarget.class);
        PauseByDeploymentNameCommand command =
            PauseByDeploymentNameCommand.create(namespace, deploymentName, adminPort, 5000, 3000L);

        when(kubernetesClient.resolvePauseTargetByDeploymentName(namespace, deploymentName, adminPort))
            .thenReturn(target);
        when(scalarAdminClientFactory.createClient(target))
            .thenThrow(new RuntimeException("Client factory error"));

        // Act & Assert
        assertThatThrownBy(() -> applicationService.execute(command))
            .isInstanceOf(PauserException.class)
            .hasMessage("Failed to initialize the Scalar Admin client.");
      }
    }
  }
}
