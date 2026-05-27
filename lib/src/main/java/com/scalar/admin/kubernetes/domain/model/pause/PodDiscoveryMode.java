package com.scalar.admin.kubernetes.domain.model.pause;

import javax.annotation.Nullable;

/**
 * Enum representing the mode for discovering target pods to pause.
 *
 * <p>This enum defines how the system identifies which pods should be paused. Different modes
 * provide different strategies for pod discovery in a Kubernetes cluster.
 */
public enum PodDiscoveryMode {
  /**
   * Discover pods by Helm release name.
   *
   * <p>In this mode, the system identifies pods by looking for resources labeled with the
   * specified Helm release name. This is the default and original behavior.
   */
  HELM_RELEASE("helm-release");

  private final String value;

  PodDiscoveryMode(String value) {
    this.value = value;
  }

  /**
   * Returns the string value of this mode.
   *
   * @return the string value (e.g., "helm-release")
   */
  public String getValue() {
    return value;
  }

  /**
   * Converts a string value to PodDiscoveryMode enum.
   *
   * <p>This method uses case-insensitive matching for better user experience, consistent with
   * picocli's setCaseInsensitiveEnumValuesAllowed(true) setting used in the CLI layer.
   *
   * @param value the string value (case-insensitive)
   * @return the corresponding PodDiscoveryMode
   * @throws IllegalArgumentException if the value is invalid
   */
  public static PodDiscoveryMode fromValue(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("podDiscoveryMode value cannot be null or blank");
    }

    // Case-insensitive comparison for consistency with CLI's setCaseInsensitiveEnumValuesAllowed
    for (PodDiscoveryMode mode : values()) {
      if (mode.value.equalsIgnoreCase(value)) {
        return mode;
      }
    }

    throw new IllegalArgumentException(
        "Invalid podDiscoveryMode: " + value + ". Valid values are: helm-release");
  }

  /**
   * Validates the required parameters for this discovery mode.
   *
   * <p>Phase 1: Only validates helmReleaseName (deploymentName and adminPort don't exist yet).
   *
   * @param helmReleaseName the Helm release name (required for HELM_RELEASE mode)
   * @throws IllegalArgumentException if required parameters are missing or invalid for this mode
   */
  public void validate(@Nullable String helmReleaseName) {
    // Phase 1: Only HELM_RELEASE exists
    if (helmReleaseName == null || helmReleaseName.isBlank()) {
      throw new IllegalArgumentException(
          "helmReleaseName is required when podDiscoveryMode is HELM_RELEASE");
    }
  }
}
