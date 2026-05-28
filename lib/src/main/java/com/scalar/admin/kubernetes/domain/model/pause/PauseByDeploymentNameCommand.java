package com.scalar.admin.kubernetes.domain.model.pause;

import javax.annotation.Nullable;

/**
 * Command to pause pods identified by deployment name.
 *
 * <p>This command represents the use case of pausing all pods that belong to a specific deployment
 * in a given namespace. The admin port must be explicitly specified as it cannot be auto-detected
 * from deployment labels.
 *
 * @param namespace the Kubernetes namespace where the deployment exists
 * @param deploymentName the name of the deployment
 * @param adminPort the admin port number for the Scalar product
 * @param pauseDuration the duration to pause in milliseconds
 * @param maxPauseWaitTime the maximum wait time (in milliseconds) for pause operation to complete,
 *     null for default
 * @param tlsConfig the TLS configuration for secure communication, null for non-TLS communication
 */
public record PauseByDeploymentNameCommand(
    String namespace,
    String deploymentName,
    int adminPort,
    int pauseDuration,
    @Nullable Long maxPauseWaitTime,
    @Nullable TlsConfig tlsConfig)
    implements PauseCommand {

  /**
   * Compact constructor with validation.
   *
   * @param namespace the Kubernetes namespace (required)
   * @param deploymentName the deployment name (required)
   * @param adminPort the admin port number (must be 0-65535)
   * @param pauseDuration the pause duration in milliseconds (must be positive)
   * @param maxPauseWaitTime the maximum wait time (optional)
   * @param tlsConfig the TLS configuration (optional)
   * @throws IllegalArgumentException if required parameters are null or invalid
   */
  public PauseByDeploymentNameCommand {
    if (namespace == null || namespace.isBlank()) {
      throw new IllegalArgumentException("namespace is required");
    }
    if (deploymentName == null || deploymentName.isBlank()) {
      throw new IllegalArgumentException("deploymentName is required");
    }
    if (adminPort < 0 || adminPort > 65535) {
      throw new IllegalArgumentException(
          "adminPort must be between 0 and 65535, but was: " + adminPort);
    }
    if (pauseDuration < 1) {
      throw new IllegalArgumentException(
          "pauseDuration must be greater than 0 millisecond, but was: " + pauseDuration);
    }
  }

  /**
   * Creates a command for pausing pods without TLS.
   *
   * @param namespace the Kubernetes namespace
   * @param deploymentName the deployment name
   * @param adminPort the admin port number
   * @param pauseDuration the pause duration in milliseconds
   * @param maxPauseWaitTime the maximum wait time in milliseconds
   * @return a new PauseByDeploymentNameCommand without TLS
   */
  public static PauseByDeploymentNameCommand create(
      String namespace,
      String deploymentName,
      int adminPort,
      int pauseDuration,
      Long maxPauseWaitTime) {
    return new PauseByDeploymentNameCommand(
        namespace, deploymentName, adminPort, pauseDuration, maxPauseWaitTime, null);
  }

  /**
   * Creates a command for pausing pods with TLS enabled.
   *
   * @param namespace the Kubernetes namespace
   * @param deploymentName the deployment name
   * @param adminPort the admin port number
   * @param pauseDuration the pause duration in milliseconds
   * @param maxPauseWaitTime the maximum wait time in milliseconds
   * @param caRootCert the CA root certificate
   * @param overrideAuthority the override authority for TLS
   * @return a new PauseByDeploymentNameCommand with TLS configuration
   */
  public static PauseByDeploymentNameCommand createWithTls(
      String namespace,
      String deploymentName,
      int adminPort,
      int pauseDuration,
      Long maxPauseWaitTime,
      String caRootCert,
      String overrideAuthority) {
    return new PauseByDeploymentNameCommand(
        namespace,
        deploymentName,
        adminPort,
        pauseDuration,
        maxPauseWaitTime,
        new TlsConfig(caRootCert, overrideAuthority));
  }

  /**
   * Returns the pod discovery mode for this command.
   *
   * @return DEPLOYMENT mode
   */
  @Override
  public PodDiscoveryMode podDiscoveryMode() {
    return PodDiscoveryMode.DEPLOYMENT;
  }
}
