package com.scalar.admin.kubernetes.domain.client;

import com.scalar.admin.kubernetes.domain.exception.PauserException;
import com.scalar.admin.kubernetes.domain.model.pause.PauseTarget;

/**
 * Client interface for interacting with Kubernetes cluster to resolve pause targets.
 *
 * <p>This client is responsible for discovering and resolving {@link PauseTarget} aggregates from
 * Kubernetes resources based on various discovery methods.
 */
public interface KubernetesClient {

  /**
   * Resolves a pause target from a Helm release.
   *
   * <p>This method discovers pods, deployment, and service created by the specified Helm release,
   * and constructs a {@link PauseTarget} aggregate containing all necessary information for pause
   * operations.
   *
   * @param namespace the Kubernetes namespace where the Helm release is deployed
   * @param helmReleaseName the name of the Helm release
   * @return a PauseTarget aggregate containing pods, deployment, and admin port information
   * @throws PauserException if the target cannot be resolved or if there are issues with the
   *     Kubernetes API
   */
  PauseTarget resolvePauseTargetByHelmRelease(String namespace, String helmReleaseName)
      throws PauserException;
}
