package com.scalar.admin.kubernetes.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.scalar.admin.kubernetes.domain.client.ScalarAdminClient;
import com.scalar.admin.kubernetes.domain.exception.PauserException;
import com.scalar.admin.kubernetes.domain.model.pause.PauseByHelmReleaseCommand;
import com.scalar.admin.kubernetes.domain.model.pause.PauseDuration;
import com.scalar.admin.kubernetes.domain.model.pause.PauseTarget;
import com.scalar.admin.kubernetes.domain.model.pause.TlsConfig;
import com.scalar.admin.kubernetes.domain.repository.PauseTargetRepository;
import com.scalar.admin.kubernetes.domain.service.PauseService;
import com.scalar.admin.kubernetes.infrastructure.client.ScalarAdminClientFactory;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PauseApplicationServiceTest {

  private PauseTargetRepository repository;
  private ScalarAdminClientFactory clientFactory;
  private PauseService pauseService;
  private PauseApplicationService applicationService;

  @BeforeEach
  void beforeEach() {
    repository = mock(PauseTargetRepository.class);
    clientFactory = mock(ScalarAdminClientFactory.class);
    pauseService = mock(PauseService.class);
    applicationService = new PauseApplicationService(repository, clientFactory, pauseService);
  }

  @Nested
  class Constructor {
    @Test
    void constructor_WithValidArgs_CreateInstance() {
      // Act & Assert
      assertDoesNotThrow(
          () -> new PauseApplicationService(repository, clientFactory, pauseService));
    }

    @Test
    void constructor_WithNullRepository_ThrowIllegalArgumentException() {
      // Act & Assert
      IllegalArgumentException thrown =
          assertThrows(
              IllegalArgumentException.class,
              () -> new PauseApplicationService(null, clientFactory, pauseService));
      assertEquals("pauseTargetRepository is required", thrown.getMessage());
    }

    @Test
    void constructor_WithNullClientFactory_ThrowIllegalArgumentException() {
      // Act & Assert
      IllegalArgumentException thrown =
          assertThrows(
              IllegalArgumentException.class,
              () -> new PauseApplicationService(repository, null, pauseService));
      assertEquals("clientFactory is required", thrown.getMessage());
    }

    @Test
    void constructor_WithNullPauseService_ThrowIllegalArgumentException() {
      // Act & Assert
      IllegalArgumentException thrown =
          assertThrows(
              IllegalArgumentException.class,
              () -> new PauseApplicationService(repository, clientFactory, null));
      assertEquals("pauseService is required", thrown.getMessage());
    }
  }

  @Nested
  class Execute {
    @Test
    void execute_WithNonTlsCommand_SuccessfullyPause() throws PauserException {
      // Arrange
      String namespace = "test-ns";
      String helmReleaseName = "test-release";
      int pauseDuration = 5000;
      Long maxPauseWaitTime = 3000L;

      PauseTarget target = mock(PauseTarget.class);
      ScalarAdminClient client = mock(ScalarAdminClient.class);
      PauseDuration expected = new PauseDuration(Instant.now(), Instant.now());

      PauseByHelmReleaseCommand command =
          PauseByHelmReleaseCommand.create(
              namespace, helmReleaseName, pauseDuration, maxPauseWaitTime);

      when(repository.findByHelmRelease(namespace, helmReleaseName)).thenReturn(target);
      when(clientFactory.createClient(target)).thenReturn(client);
      when(pauseService.pause(eq(target), any(), eq(client), eq(pauseDuration), eq(maxPauseWaitTime)))
          .thenReturn(expected);

      // Act
      PauseDuration actual = applicationService.execute(command);

      // Assert
      assertEquals(expected, actual);
      verify(repository).findByHelmRelease(namespace, helmReleaseName);
      verify(clientFactory).createClient(target);
      verify(pauseService)
          .pause(eq(target), any(), eq(client), eq(pauseDuration), eq(maxPauseWaitTime));
    }

    @Test
    void execute_WithTlsCommand_SuccessfullyPause() throws PauserException {
      // Arrange
      String namespace = "test-ns";
      String helmReleaseName = "test-release";
      int pauseDuration = 5000;
      Long maxPauseWaitTime = 3000L;
      String caRootCert = "dummyCert";
      String overrideAuthority = "dummyAuthority";

      PauseTarget target = mock(PauseTarget.class);
      ScalarAdminClient client = mock(ScalarAdminClient.class);
      PauseDuration expected = new PauseDuration(Instant.now(), Instant.now());

      PauseByHelmReleaseCommand command =
          PauseByHelmReleaseCommand.createWithTls(
              namespace,
              helmReleaseName,
              pauseDuration,
              maxPauseWaitTime,
              caRootCert,
              overrideAuthority);

      when(repository.findByHelmRelease(namespace, helmReleaseName)).thenReturn(target);
      when(clientFactory.createClient(eq(target), any(TlsConfig.class))).thenReturn(client);
      when(pauseService.pause(eq(target), any(), eq(client), eq(pauseDuration), eq(maxPauseWaitTime)))
          .thenReturn(expected);

      // Act
      PauseDuration actual = applicationService.execute(command);

      // Assert
      assertEquals(expected, actual);
      verify(repository).findByHelmRelease(namespace, helmReleaseName);
      verify(clientFactory).createClient(eq(target), any(TlsConfig.class));
      verify(pauseService)
          .pause(eq(target), any(), eq(client), eq(pauseDuration), eq(maxPauseWaitTime));
    }

    @Test
    void execute_WhenRepositoryThrowsException_ThrowPauserException() throws PauserException {
      // Arrange
      String namespace = "test-ns";
      String helmReleaseName = "test-release";

      PauseByHelmReleaseCommand command =
          PauseByHelmReleaseCommand.create(namespace, helmReleaseName, 5000, 3000L);

      when(repository.findByHelmRelease(namespace, helmReleaseName))
          .thenThrow(new RuntimeException("Repository error"));

      // Act & Assert
      PauserException thrown =
          assertThrows(PauserException.class, () -> applicationService.execute(command));
      assertEquals("Failed to find the target pods to pause.", thrown.getMessage());
    }

    @Test
    void execute_WhenClientFactoryThrowsException_ThrowPauserException() throws PauserException {
      // Arrange
      String namespace = "test-ns";
      String helmReleaseName = "test-release";

      PauseTarget target = mock(PauseTarget.class);
      PauseByHelmReleaseCommand command =
          PauseByHelmReleaseCommand.create(namespace, helmReleaseName, 5000, 3000L);

      when(repository.findByHelmRelease(namespace, helmReleaseName)).thenReturn(target);
      when(clientFactory.createClient(target))
          .thenThrow(new RuntimeException("Client factory error"));

      // Act & Assert
      PauserException thrown =
          assertThrows(PauserException.class, () -> applicationService.execute(command));
      assertEquals("Failed to initialize the Scalar Admin client.", thrown.getMessage());
    }
  }
}
