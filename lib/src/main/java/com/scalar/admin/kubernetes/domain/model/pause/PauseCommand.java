package com.scalar.admin.kubernetes.domain.model.pause;

/**
 * Sealed interface representing different pause operation commands.
 *
 * <p>This sealed interface defines all possible pause operation use cases. Using a sealed
 * interface provides type safety and ensures exhaustive handling in switch expressions.
 *
 * <p>Each implementation represents a specific way to identify and pause target pods in a
 * Kubernetes cluster.
 */
public sealed interface PauseCommand
    permits PauseByHelmReleaseCommand, PauseByDeploymentNameCommand {

  /** Shared validation error messages for Command compact constructors. */
  String NAMESPACE_REQUIRED_ERROR = "namespace is required";
  String HELM_RELEASE_NAME_REQUIRED_ERROR = "helmReleaseName is required";
  String DEPLOYMENT_NAME_REQUIRED_ERROR = "deploymentName is required";
  String PAUSE_DURATION_ERROR = "pauseDuration must be greater than 0 millisecond, but was: %d";
  String ADMIN_PORT_ERROR = "adminPort must be between 1 and 65535, but was: %d";

  /**
   * Returns the pod discovery mode for this command.
   *
   * <p>This method specifies how the system should identify target pods.
   *
   * @return the pod discovery mode
   */
  PodDiscoveryMode podDiscoveryMode();
}
