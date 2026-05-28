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

  /**
   * Returns the pod discovery mode for this command.
   *
   * <p>This method specifies how the system should identify target pods. The default
   * implementation returns HELM_RELEASE for backward compatibility.
   *
   * @return the pod discovery mode
   */
  default PodDiscoveryMode podDiscoveryMode() {
    return PodDiscoveryMode.HELM_RELEASE;
  }
}
