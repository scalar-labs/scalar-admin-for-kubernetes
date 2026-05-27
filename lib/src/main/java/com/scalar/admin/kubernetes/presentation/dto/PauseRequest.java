package com.scalar.admin.kubernetes.presentation.dto;

import javax.annotation.Nullable;

/**
 * Request DTO for pause operations.
 *
 * <p>This DTO transfers pause request data from the CLI layer to the presentation layer,
 * encapsulating all parameters needed for a pause operation.
 *
 * @param namespace the Kubernetes namespace where the target is deployed
 * @param podDiscoveryMode the mode to discover target pods (e.g., "helm-release", "deployment")
 * @param helmReleaseName the name of the Helm release (required for "helm-release" mode)
 * @param deploymentName the name of the deployment (required for "deployment" mode)
 * @param adminPort the admin port number (required for "deployment" mode)
 * @param pauseDuration the duration to pause in milliseconds
 * @param maxPauseWaitTime the maximum wait time in milliseconds for pause operation to complete,
 *     null for default
 * @param tlsEnabled whether TLS is enabled for communication
 * @param caRootCert the CA root certificate for TLS verification, null if TLS is disabled
 * @param overrideAuthority the override authority for TLS, null if TLS is disabled
 */
public record PauseRequest(
    String namespace,
    String podDiscoveryMode,
    @Nullable String helmReleaseName,
    @Nullable String deploymentName,
    @Nullable Integer adminPort,
    int pauseDuration,
    @Nullable Long maxPauseWaitTime,
    boolean tlsEnabled,
    @Nullable String caRootCert,
    @Nullable String overrideAuthority) {

  /**
   * Compact constructor with validation.
   *
   * @throws IllegalArgumentException if required parameters are null or invalid
   */
  public PauseRequest {
    if (namespace == null || namespace.isBlank()) {
      throw new IllegalArgumentException("namespace is required");
    }
    if (podDiscoveryMode == null || podDiscoveryMode.isBlank()) {
      throw new IllegalArgumentException("podDiscoveryMode is required");
    }
    if (pauseDuration < 1) {
      throw new IllegalArgumentException(
          "pauseDuration must be greater than 0, but was: " + pauseDuration);
    }
    if (tlsEnabled) {
      if (caRootCert == null || caRootCert.isBlank()) {
        throw new IllegalArgumentException("caRootCert is required when tlsEnabled is true");
      }
      if (overrideAuthority == null || overrideAuthority.isBlank()) {
        throw new IllegalArgumentException(
            "overrideAuthority is required when tlsEnabled is true");
      }
    }
  }
}
