package com.scalar.admin.kubernetes;

import javax.annotation.Nullable;

public enum PodDiscoveryMode {
  /** Discover target pods by Helm release name using label-based auto-detection. */
  HELM_RELEASE;

  /**
   * Validates that CLI options are consistent with this mode. Checks for missing required options
   * and rejects options belonging to other modes.
   *
   * @param helmReleaseName The Helm release name specified by --release-name.
   * @param deploymentName The Deployment name specified by --deployment-name.
   * @param adminPort The admin port specified by --admin-port.
   * @throws IllegalArgumentException if the options are inconsistent with this mode.
   */
  void validate(
      @Nullable String helmReleaseName,
      @Nullable String deploymentName,
      @Nullable Integer adminPort)
      throws IllegalArgumentException {
    switch (this) {
      case HELM_RELEASE:
        if (helmReleaseName == null) {
          throw new IllegalArgumentException(
              "--release-name is required when --pod-discovery-mode is helm-release.");
        }
        if (deploymentName != null || adminPort != null) {
          throw new IllegalArgumentException(
              "--deployment-name and --admin-port cannot be used"
                  + " when --pod-discovery-mode is helm-release.");
        }
        break;
    }
  }
}
