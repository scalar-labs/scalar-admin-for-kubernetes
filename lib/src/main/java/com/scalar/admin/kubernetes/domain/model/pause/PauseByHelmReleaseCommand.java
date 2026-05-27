package com.scalar.admin.kubernetes.domain.model.pause;

import javax.annotation.Nullable;

/**
 * Command to pause pods identified by Helm release name.
 *
 * <p>This command represents the use case of pausing all pods that belong to a specific Helm
 * release in a given namespace. Optional TLS configuration can be provided for secure
 * communication with Scalar Admin interfaces.
 *
 * @param namespace the Kubernetes namespace where the Helm release is deployed
 * @param helmReleaseName the name of the Helm release
 * @param pauseDuration the duration to pause in milliseconds
 * @param maxPauseWaitTime the maximum wait time (in milliseconds) for pause operation to complete,
 *     null for default
 * @param tlsConfig the TLS configuration for secure communication, null for non-TLS communication
 */
public record PauseByHelmReleaseCommand(
    String namespace,
    String helmReleaseName,
    int pauseDuration,
    @Nullable Long maxPauseWaitTime,
    @Nullable TlsConfig tlsConfig)
    implements PauseCommand {

  /**
   * Compact constructor with validation.
   *
   * @param namespace the Kubernetes namespace (required)
   * @param helmReleaseName the Helm release name (required)
   * @param pauseDuration the pause duration in milliseconds (must be positive)
   * @param maxPauseWaitTime the maximum wait time (optional)
   * @param tlsConfig the TLS configuration (optional)
   * @throws IllegalArgumentException if required parameters are null or invalid
   */
  public PauseByHelmReleaseCommand {
    if (namespace == null || namespace.isBlank()) {
      throw new IllegalArgumentException("namespace is required");
    }
    if (helmReleaseName == null || helmReleaseName.isBlank()) {
      throw new IllegalArgumentException("helmReleaseName is required");
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
   * @param helmReleaseName the Helm release name
   * @param pauseDuration the pause duration in milliseconds
   * @param maxPauseWaitTime the maximum wait time in milliseconds
   * @return a new PauseByHelmReleaseCommand without TLS
   */
  public static PauseByHelmReleaseCommand create(
      String namespace, String helmReleaseName, int pauseDuration, Long maxPauseWaitTime) {
    return new PauseByHelmReleaseCommand(
        namespace, helmReleaseName, pauseDuration, maxPauseWaitTime, null);
  }

  /**
   * Creates a command for pausing pods with TLS enabled.
   *
   * @param namespace the Kubernetes namespace
   * @param helmReleaseName the Helm release name
   * @param pauseDuration the pause duration in milliseconds
   * @param maxPauseWaitTime the maximum wait time in milliseconds
   * @param caRootCert the CA root certificate
   * @param overrideAuthority the override authority for TLS
   * @return a new PauseByHelmReleaseCommand with TLS configuration
   */
  public static PauseByHelmReleaseCommand createWithTls(
      String namespace,
      String helmReleaseName,
      int pauseDuration,
      Long maxPauseWaitTime,
      String caRootCert,
      String overrideAuthority) {
    return new PauseByHelmReleaseCommand(
        namespace,
        helmReleaseName,
        pauseDuration,
        maxPauseWaitTime,
        new TlsConfig(caRootCert, overrideAuthority));
  }

  /**
   * Returns the pod discovery mode for this command.
   *
   * @return HELM_RELEASE mode
   */
  @Override
  public PodDiscoveryMode podDiscoveryMode() {
    return PodDiscoveryMode.HELM_RELEASE;
  }
}
