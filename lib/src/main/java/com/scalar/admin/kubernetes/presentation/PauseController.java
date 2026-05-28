package com.scalar.admin.kubernetes.presentation;

import com.scalar.admin.kubernetes.application.PauseApplicationService;
import com.scalar.admin.kubernetes.application.dto.PauseDurationDto;
import com.scalar.admin.kubernetes.domain.exception.PauserException;
import com.scalar.admin.kubernetes.domain.model.pause.PauseByDeploymentNameCommand;
import com.scalar.admin.kubernetes.domain.model.pause.PauseByHelmReleaseCommand;
import com.scalar.admin.kubernetes.domain.model.pause.PauseCommand;
import com.scalar.admin.kubernetes.domain.model.pause.PodDiscoveryMode;
import com.scalar.admin.kubernetes.presentation.dto.PauseRequest;
import javax.inject.Inject;

/**
 * Controller for pause operations.
 *
 * <p>This controller handles the presentation layer concerns, coordinating the execution of pause
 * commands by delegating to the application service.
 */
public class PauseController {

  private final PauseApplicationService applicationService;

  /**
   * Creates a PauseController with the given application service.
   *
   * @param applicationService the application service for executing pause operations
   */
  @Inject
  public PauseController(PauseApplicationService applicationService) {
    if (applicationService == null) {
      throw new IllegalArgumentException("applicationService is required");
    }
    this.applicationService = applicationService;
  }

  /**
   * Executes a pause operation based on the given request.
   *
   * @param request the pause request containing all necessary parameters
   * @return DTO containing the start and end time of the pause operation
   * @throws PauserException when the pause operation fails
   * @throws IllegalArgumentException when the request or podDiscoveryMode is invalid
   */
  public PauseDurationDto pause(PauseRequest request) throws PauserException {
    if (request == null) {
      throw new IllegalArgumentException("request is required");
    }

    PodDiscoveryMode mode = PodDiscoveryMode.fromValue(request.podDiscoveryMode());
    mode.validate(request.helmReleaseName(), request.deploymentName(), request.adminPort());

    PauseCommand command = createCommand(request, mode);

    return applicationService.execute(command);
  }

  PauseCommand createCommand(PauseRequest request, PodDiscoveryMode mode) {
    return switch (mode) {
      case HELM_RELEASE -> createHelmReleaseCommand(request);
      case DEPLOYMENT -> createDeploymentCommand(request);
    };
  }

  PauseByHelmReleaseCommand createHelmReleaseCommand(PauseRequest request) {
    return request.tlsEnabled()
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
  }

  PauseByDeploymentNameCommand createDeploymentCommand(PauseRequest request) {
    return request.tlsEnabled()
        ? PauseByDeploymentNameCommand.createWithTls(
            request.namespace(),
            request.deploymentName(),
            request.adminPort(),
            request.pauseDuration(),
            request.maxPauseWaitTime(),
            request.caRootCert(),
            request.overrideAuthority())
        : PauseByDeploymentNameCommand.create(
            request.namespace(),
            request.deploymentName(),
            request.adminPort(),
            request.pauseDuration(),
            request.maxPauseWaitTime());
  }
}
