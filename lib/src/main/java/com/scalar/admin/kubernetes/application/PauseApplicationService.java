package com.scalar.admin.kubernetes.application;

import com.scalar.admin.kubernetes.application.dto.PauseDurationDto;
import com.scalar.admin.kubernetes.domain.client.ScalarAdminClient;
import com.scalar.admin.kubernetes.domain.exception.PauserException;
import com.scalar.admin.kubernetes.domain.model.pause.PauseByDeploymentNameCommand;
import com.scalar.admin.kubernetes.domain.model.pause.PauseByHelmReleaseCommand;
import com.scalar.admin.kubernetes.domain.model.pause.PauseCommand;
import com.scalar.admin.kubernetes.domain.model.pause.PauseDuration;
import com.scalar.admin.kubernetes.domain.model.pause.PauseTarget;
import com.scalar.admin.kubernetes.domain.model.pause.TlsConfig;
import com.scalar.admin.kubernetes.domain.client.KubernetesClient;
import com.scalar.admin.kubernetes.domain.service.PauseService;
import com.scalar.admin.kubernetes.domain.client.ScalarAdminClientFactory;
import com.scalar.admin.kubernetes.domain.service.PauseService.PauseTargetSupplier;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;

/**
 * Application service for pause operations.
 *
 * <p>This service coordinates the pause operation by:
 *
 * <ol>
 *   <li>Resolving the pause target from Kubernetes based on the command
 *   <li>Creating the appropriate Scalar Admin client (with or without TLS)
 *   <li>Delegating to the domain service for business logic execution
 * </ol>
 *
 * <p>This class is not thread-safe because it causes side effects in the states of target pods.
 */
@NotThreadSafe
public class PauseApplicationService {

  private final KubernetesClient kubernetesClient;
  private final ScalarAdminClientFactory clientFactory;
  private final PauseService pauseService;

  /**
   * Creates a PauseApplicationService with the given dependencies.
   *
   * @param kubernetesClient client for resolving pause targets from Kubernetes
   * @param clientFactory factory for creating Scalar Admin clients
   * @param pauseService domain service for pause business logic
   */
  @Inject
  public PauseApplicationService(
      KubernetesClient kubernetesClient,
      ScalarAdminClientFactory clientFactory,
      PauseService pauseService) {
    if (kubernetesClient == null) {
      throw new IllegalArgumentException("kubernetesClient is required");
    }
    if (clientFactory == null) {
      throw new IllegalArgumentException("clientFactory is required");
    }
    if (pauseService == null) {
      throw new IllegalArgumentException("pauseService is required");
    }
    this.kubernetesClient = kubernetesClient;
    this.clientFactory = clientFactory;
    this.pauseService = pauseService;
  }

  /**
   * Executes a pause operation based on the given command.
   *
   * @param command the pause command specifying the operation details
   * @return DTO containing the start and end time of the pause operation
   * @throws PauserException when the pause operation fails
   * @throws IllegalArgumentException when the command is null
   */
  public PauseDurationDto execute(PauseCommand command) throws PauserException {
    if (command == null) {
      throw new IllegalArgumentException("command is required");
    }
    return switch (command) {
      case PauseByHelmReleaseCommand cmd -> executePauseByHelmRelease(cmd);
      case PauseByDeploymentNameCommand cmd -> executePauseByDeploymentName(cmd);
    };
  }

  private PauseDurationDto executePauseByHelmRelease(PauseByHelmReleaseCommand command)
      throws PauserException {
    return executePause(
        () ->
            kubernetesClient.resolvePauseTargetByHelmRelease(
                command.namespace(), command.helmReleaseName()),
        command.tlsConfig(),
        command.pauseDuration(),
        command.maxPauseWaitTime());
  }

  private PauseDurationDto executePauseByDeploymentName(PauseByDeploymentNameCommand command)
      throws PauserException {
    return executePause(
        () ->
            kubernetesClient.resolvePauseTargetByDeploymentName(
                command.namespace(), command.deploymentName(), command.adminPort()),
        command.tlsConfig(),
        command.pauseDuration(),
        command.maxPauseWaitTime());
  }

  /**
   * Executes a pause operation with the given target supplier and configuration.
   *
   * @param targetSupplier supplier function to retrieve the pause target
   * @param tlsConfig TLS configuration (null for non-TLS)
   * @param pauseDuration the duration to pause in milliseconds
   * @param maxPauseWaitTime the maximum wait time for pause operation (null for default)
   * @return DTO containing the start and end time of the pause operation
   * @throws PauserException when the pause operation fails
   */
  private PauseDurationDto executePause(
      PauseTargetSupplier targetSupplier,
      @Nullable TlsConfig tlsConfig,
      int pauseDuration,
      @Nullable Long maxPauseWaitTime)
      throws PauserException {

    // Get the pause target before pause
    PauseTarget targetBeforePause;
    try {
      targetBeforePause = targetSupplier.get();
    } catch (Exception e) {
      throw new PauserException("Failed to find the target pods to pause.", e);
    }

    // Create the appropriate client (with or without TLS)
    ScalarAdminClient client;
    try {
      if (tlsConfig != null) {
        client = clientFactory.createClient(targetBeforePause, tlsConfig);
      } else {
        client = clientFactory.createClient(targetBeforePause);
      }
    } catch (Exception e) {
      throw new PauserException("Failed to initialize the Scalar Admin client.", e);
    }

    // Execute the pause operation through the domain service
    PauseDuration duration =
        pauseService.pause(
            targetBeforePause, targetSupplier, client, pauseDuration, maxPauseWaitTime);

    // Convert domain object to DTO
    return new PauseDurationDto(
        duration.startTime().toEpochMilli(), duration.endTime().toEpochMilli());
  }
}
