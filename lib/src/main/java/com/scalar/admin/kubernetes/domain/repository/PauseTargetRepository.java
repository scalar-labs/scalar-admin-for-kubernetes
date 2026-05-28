package com.scalar.admin.kubernetes.domain.repository;

import com.scalar.admin.kubernetes.domain.exception.PauserException;
import com.scalar.admin.kubernetes.domain.model.pause.PauseTarget;

/**
 * Repository interface for finding pause targets in a Kubernetes cluster.
 *
 * <p>This repository is responsible for discovering and retrieving {@link PauseTarget} aggregates
 * based on deployment information such as Helm release names.
 */
public interface PauseTargetRepository {

  /**
   * Finds a pause target by Helm release name in the specified namespace.
   *
   * <p>This method discovers pods, deployments, and services created by the specified Helm release
   * and constructs a {@link PauseTarget} aggregate containing all necessary information for pause
   * operations.
   *
   * @param namespace the Kubernetes namespace where the Helm release is deployed
   * @param helmReleaseName the name of the Helm release
   * @return a PauseTarget aggregate containing pods, deployment, and admin port information
   * @throws PauserException if the target cannot be found or if there are issues with the
   *     Kubernetes API
   */
  PauseTarget findByHelmRelease(String namespace, String helmReleaseName) throws PauserException;

  /**
   * Finds a pause target by deployment name in the specified namespace.
   *
   * <p>This method discovers pods that belong to the specified deployment and constructs a {@link
   * PauseTarget} aggregate. Unlike the Helm release-based discovery, this method requires an
   * explicit admin port since it cannot be auto-detected from deployment labels.
   *
   * @param namespace the Kubernetes namespace where the deployment exists
   * @param deploymentName the name of the deployment
   * @param adminPort the admin port number for the Scalar product
   * @return a PauseTarget aggregate containing pods, deployment, and admin port information
   * @throws PauserException if the target cannot be found or if there are issues with the
   *     Kubernetes API
   */
  PauseTarget findByDeploymentName(String namespace, String deploymentName, int adminPort)
      throws PauserException;
}
