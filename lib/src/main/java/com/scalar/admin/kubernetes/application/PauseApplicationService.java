package com.scalar.admin.kubernetes.application;

import com.scalar.admin.kubernetes.domain.client.ScalarAdminClient;
import com.scalar.admin.kubernetes.domain.exception.PauserException;
import com.scalar.admin.kubernetes.domain.model.pause.PauseByHelmReleaseCommand;
import com.scalar.admin.kubernetes.domain.model.pause.PauseCommand;
import com.scalar.admin.kubernetes.domain.model.pause.PauseDuration;
import com.scalar.admin.kubernetes.domain.model.pause.PauseTarget;
import com.scalar.admin.kubernetes.domain.repository.PauseTargetRepository;
import com.scalar.admin.kubernetes.domain.service.PauseService;
import com.scalar.admin.kubernetes.infrastructure.client.ScalarAdminClientFactory;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Application service for pause operations.
 *
 * <p>This service coordinates the pause operation by:
 *
 * <ol>
 *   <li>Retrieving the pause target from the repository based on the command
 *   <li>Creating the appropriate Scalar Admin client (with or without TLS)
 *   <li>Delegating to the domain service for business logic execution
 * </ol>
 *
 * <p>This class is not thread-safe because it causes side effects in the states of target pods.
 */
@NotThreadSafe
public class PauseApplicationService {

  private final PauseTargetRepository pauseTargetRepository;
  private final ScalarAdminClientFactory clientFactory;
  private final PauseService pauseService;

  /**
   * Creates a PauseApplicationService with the given dependencies.
   *
   * @param pauseTargetRepository repository for retrieving pause targets
   * @param clientFactory factory for creating Scalar Admin clients
   * @param pauseService domain service for pause business logic
   */
  public PauseApplicationService(
      PauseTargetRepository pauseTargetRepository,
      ScalarAdminClientFactory clientFactory,
      PauseService pauseService) {
    if (pauseTargetRepository == null) {
      throw new IllegalArgumentException("pauseTargetRepository is required");
    }
    if (clientFactory == null) {
      throw new IllegalArgumentException("clientFactory is required");
    }
    if (pauseService == null) {
      throw new IllegalArgumentException("pauseService is required");
    }
    this.pauseTargetRepository = pauseTargetRepository;
    this.clientFactory = clientFactory;
    this.pauseService = pauseService;
  }

  /**
   * Executes a pause operation based on the given command.
   *
   * @param command the pause command specifying the operation details
   * @return the start and end time of the pause operation
   * @throws PauserException when the pause operation fails
   */
  public PauseDuration execute(PauseCommand command) throws PauserException {
    return switch (command) {
      case PauseByHelmReleaseCommand cmd -> executePauseByHelmRelease(cmd);
    };
  }

  private PauseDuration executePauseByHelmRelease(PauseByHelmReleaseCommand command)
      throws PauserException {
    // Get the pause target before pause
    PauseTarget targetBeforePause;
    try {
      targetBeforePause =
          pauseTargetRepository.findByHelmRelease(command.namespace(), command.helmReleaseName());
    } catch (Exception e) {
      throw new PauserException("Failed to find the target pods to pause.", e);
    }

    // Create the appropriate client (with or without TLS)
    ScalarAdminClient client;
    try {
      if (command.tlsConfig() != null) {
        client = clientFactory.createClient(targetBeforePause, command.tlsConfig());
      } else {
        client = clientFactory.createClient(targetBeforePause);
      }
    } catch (Exception e) {
      throw new PauserException("Failed to initialize the Scalar Admin client.", e);
    }

    // Execute the pause operation through the domain service
    return pauseService.pause(
        targetBeforePause,
        () -> pauseTargetRepository.findByHelmRelease(command.namespace(), command.helmReleaseName()),
        client,
        command.pauseDuration(),
        command.maxPauseWaitTime());
  }
}
