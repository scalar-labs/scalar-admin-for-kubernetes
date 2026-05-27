package com.scalar.admin.kubernetes.presentation;

import com.scalar.admin.kubernetes.application.PauseApplicationService;
import com.scalar.admin.kubernetes.application.dto.PauseDurationDto;
import com.scalar.admin.kubernetes.domain.exception.PauserException;
import com.scalar.admin.kubernetes.domain.model.pause.PauseByHelmReleaseCommand;
import com.scalar.admin.kubernetes.domain.model.pause.PodDiscoveryMode;
import com.scalar.admin.kubernetes.domain.repository.PauseTargetRepository;
import com.scalar.admin.kubernetes.domain.service.PauseService;
import com.scalar.admin.kubernetes.infrastructure.client.ScalarAdminClientFactory;
import com.scalar.admin.kubernetes.infrastructure.repository.PauseTargetRepositoryImpl;
import com.scalar.admin.kubernetes.presentation.dto.PauseRequest;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.Config;
import java.io.IOException;

/**
 * Controller for pause operations.
 *
 * <p>This controller handles the presentation layer concerns, including initializing the
 * application service and its dependencies, and coordinating the execution of pause commands.
 */
public class PauseController {

  private final PauseApplicationService applicationService;

  /**
   * Creates a PauseController with initialized dependencies.
   *
   * @throws PauserException when the Kubernetes client fails to be initialized
   */
  public PauseController() throws PauserException {
    try {
      Configuration.setDefaultApiClient(Config.defaultClient());
    } catch (IOException e) {
      throw new PauserException("Failed to set default Kubernetes client.", e);
    }

    PauseTargetRepository repository =
        new PauseTargetRepositoryImpl(new CoreV1Api(), new AppsV1Api());
    ScalarAdminClientFactory clientFactory = new ScalarAdminClientFactory();
    PauseService pauseService = new PauseService();

    this.applicationService = new PauseApplicationService(repository, clientFactory, pauseService);
  }

  /**
   * Executes a pause operation based on the given request.
   *
   * @param request the pause request containing all necessary parameters
   * @return DTO containing the start and end time of the pause operation
   * @throws PauserException when the pause operation fails
   * @throws IllegalArgumentException when the request or podDiscoveryMode is invalid
   * @throws UnsupportedOperationException when DEPLOYMENT mode is requested (not yet implemented)
   */
  public PauseDurationDto pause(PauseRequest request) throws PauserException {
    if (request == null) {
      throw new IllegalArgumentException("request is required");
    }

    PodDiscoveryMode mode = PodDiscoveryMode.fromValue(request.podDiscoveryMode());
    mode.validate(request.helmReleaseName(), request.deploymentName(), request.adminPort());

    if (mode == PodDiscoveryMode.DEPLOYMENT) {
      throw new UnsupportedOperationException(
          "DEPLOYMENT mode is not yet implemented. Only HELM_RELEASE mode is currently"
              + " supported.");
    }

    PauseByHelmReleaseCommand command =
        request.tlsEnabled()
            ? PauseByHelmReleaseCommand.createWithTls(
                request.namespace(),
                request.helmReleaseName(),
                request.pauseDuration(),
                request.maxPauseWaitTime(),
                request.caRootCert(),
                request.overrideAuthority())
            : PauseByHelmReleaseCommand.create(
                request.namespace(),
                request.helmReleaseName(),
                request.pauseDuration(),
                request.maxPauseWaitTime());

    return applicationService.execute(command);
  }
}
