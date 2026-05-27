package com.scalar.admin.kubernetes.presentation;

import com.scalar.admin.kubernetes.application.PauseApplicationService;
import com.scalar.admin.kubernetes.application.dto.PauseDurationDto;
import com.scalar.admin.kubernetes.domain.exception.PauserException;
import com.scalar.admin.kubernetes.domain.model.pause.PauseByHelmReleaseCommand;
import com.scalar.admin.kubernetes.domain.repository.PauseTargetRepository;
import com.scalar.admin.kubernetes.domain.service.PauseService;
import com.scalar.admin.kubernetes.infrastructure.client.ScalarAdminClientFactory;
import com.scalar.admin.kubernetes.infrastructure.repository.PauseTargetRepositoryImpl;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.Config;
import java.io.IOException;
import javax.annotation.Nullable;

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
   * Executes a pause operation by Helm release.
   *
   * @param namespace the Kubernetes namespace
   * @param helmReleaseName the Helm release name
   * @param pauseDuration the duration to pause in milliseconds
   * @param maxPauseWaitTime the maximum wait time in milliseconds, null for default
   * @return DTO containing the start and end time of the pause operation
   * @throws PauserException when the pause operation fails
   */
  public PauseDurationDto pauseByHelmRelease(
      String namespace,
      String helmReleaseName,
      int pauseDuration,
      @Nullable Long maxPauseWaitTime)
      throws PauserException {
    PauseByHelmReleaseCommand command =
        PauseByHelmReleaseCommand.create(namespace, helmReleaseName, pauseDuration, maxPauseWaitTime);
    return applicationService.execute(command);
  }

  /**
   * Executes a pause operation by Helm release with TLS enabled.
   *
   * @param namespace the Kubernetes namespace
   * @param helmReleaseName the Helm release name
   * @param pauseDuration the duration to pause in milliseconds
   * @param maxPauseWaitTime the maximum wait time in milliseconds, null for default
   * @param caRootCert the CA root certificate for TLS
   * @param overrideAuthority the override authority for TLS
   * @return DTO containing the start and end time of the pause operation
   * @throws PauserException when the pause operation fails
   */
  public PauseDurationDto pauseByHelmReleaseWithTls(
      String namespace,
      String helmReleaseName,
      int pauseDuration,
      @Nullable Long maxPauseWaitTime,
      String caRootCert,
      String overrideAuthority)
      throws PauserException {
    PauseByHelmReleaseCommand command =
        PauseByHelmReleaseCommand.createWithTls(
            namespace, helmReleaseName, pauseDuration, maxPauseWaitTime, caRootCert, overrideAuthority);
    return applicationService.execute(command);
  }
}
