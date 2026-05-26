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
public sealed interface PauseCommand permits PauseByHelmReleaseCommand {
  // Future implementations might include:
  // - PauseByDeploymentCommand
  // - PauseByLabelSelectorCommand
}
