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

  /**
   * Resolves a pause target by deployment name in the specified namespace.
   *
   * <p>This method discovers pods that belong to the specified deployment and constructs a {@link
   * PauseTarget} aggregate. Unlike the Helm release-based discovery, this method requires an
   * explicit admin port since it cannot be auto-detected from deployment labels.
   *
   * @param namespace the Kubernetes namespace where the deployment exists
   * @param deploymentName the name of the deployment
   * @param adminPort the admin port number for the Scalar product
   * @return a PauseTarget aggregate containing pods, deployment, and admin port information
   * @throws PauserException if the target cannot be resolved or if there are issues with the
   *     Kubernetes API
   */
  PauseTarget resolvePauseTargetByDeploymentName(String namespace, String deploymentName, int adminPort)
      throws PauserException;
}
